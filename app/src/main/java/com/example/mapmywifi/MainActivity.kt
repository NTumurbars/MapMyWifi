package com.example.mapmywifi
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.core.app.ShareCompat
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.mapmywifi.ui.theme.MapMyWifiTheme
import kotlin.math.ceil

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MapMyWifiTheme {
                FloorplanPickerAndDisplay()
            }
        }
    }
}

@Composable
fun FloorplanPickerAndDisplay() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()


    // AP related
    val accessPoints = remember { mutableStateListOf<AccessPointInstance>() }
    var accessPointTypes by remember { mutableStateOf(emptyList<AccessPointType>()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch Access Point Types
    LaunchedEffect(key1 = true) {
        isLoading = true
        accessPointTypes = try {
            AccessPointTypes.fetchAccessPointTypes()
        } finally {
            isLoading = false
        }
    }




    // Floorplan Bitmap
    var floorplanBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // FloorPlan Dimensions
    var showDimensionDialog by remember { mutableStateOf(false) }
    var floorplanWidthInMeters by remember { mutableStateOf<Float?>(null) }
    var floorplanHeightInMeters by remember { mutableStateOf<Float?>(null) }
    var showDialogInvalidInput by remember { mutableStateOf(false) }
    var showAccessPointSelectionDialog by remember { mutableStateOf(false) }


    // Proposal related states
    var showProposalDialog by remember { mutableStateOf(false) }
    val proposalOptions by remember { derivedStateOf { generateProposalOptions(accessPoints) } }
    val visitationFee = 15000
    val proposalSummary by remember {
        derivedStateOf {
            ProposalSummary(
                proposalOptions,
                visitationFee
            )
        }
    }


    //show the floorplan
    val pickContentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                coroutineScope.launch {
                    val bitmap = FileUtils.loadBitmapFromUri(context, it)
                    floorplanBitmap = bitmap
                    showDimensionDialog = true
                }
            }
        }


    //dimensions and scaling related variables
    var boxSize by remember { mutableStateOf(IntSize.Zero) }


    val scaleBitmapToBox = remember(floorplanBitmap, boxSize) {
        floorplanBitmap?.let { bitmap ->
            boxSize.width.toFloat() / bitmap.width.toFloat()
        } ?: 1f
    }

    val floorplanWidthInPx = boxSize.width.toFloat()


    val floorplanHeightInPx = remember(floorplanBitmap, scaleBitmapToBox) {
        floorplanBitmap?.let { bitmap ->
            bitmap.height.toFloat() * scaleBitmapToBox
        } ?: 0f
    }

    val scaleMeterToPx = remember(floorplanWidthInPx, floorplanWidthInMeters) {
        (floorplanWidthInMeters?.takeIf { it > 0 }?.let { floorplanWidthInPx / it }) ?: 1f
    }

    //omg finally this thing is working. god damn these remember states
    LaunchedEffect(floorplanWidthInMeters, floorplanHeightInMeters, accessPointTypes) {
        val widthMeters = floorplanWidthInMeters
        val heightMeters = floorplanHeightInMeters
        if (widthMeters != null && widthMeters > 0 &&
            heightMeters != null && heightMeters > 0 && accessPointTypes.isNotEmpty()) {

            val localScaleMeterToPx = floorplanWidthInPx / widthMeters

            val autoPlacedAPs = calculateAccessPointPositions(
                floorplanWidth = floorplanWidthInPx,
                floorplanHeight = floorplanHeightInPx,
                scale = localScaleMeterToPx,
                apType = accessPointTypes.first()
            )

            accessPoints.clear()
            accessPoints.addAll(autoPlacedAPs)
        }
    }





    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = { pickContentLauncher.launch("image/*") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text("Choose Floorplan")
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coordinates -> boxSize = coordinates.size }
        ) {
            floorplanBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Floorplan",
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            accessPoints.forEachIndexed { index, ap ->
                AccessPointDraggable(
                    accessPoint = ap,
                    screenScale = scaleMeterToPx,
                    onUpdate = { updatedAp ->
                        accessPoints[index] = updatedAp
                    },
                    onDelete = { deletedAp ->
                        accessPoints.removeAll { it.id == deletedAp.id }
                    }
                )
            }
        }


        Button(
            onClick = { showAccessPointSelectionDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Add Access Point")
        }

        //Generate Proposal button
        Button(
            onClick = { showProposalDialog = true },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Generate Proposal")
        }
    }

    if (showProposalDialog) {
        ProposalSummaryDialog(proposalSummary) {
            showProposalDialog = false
        }
    }

    if (showDimensionDialog && !isLoading) {

        RequestDimensionsDialog { width, height ->
            if (width > 0f && height > 0f) {
                floorplanWidthInMeters = width
                floorplanHeightInMeters = height
                showDimensionDialog = false


            } else {
                showDialogInvalidInput = true
            }
        }
    }

    if (showDialogInvalidInput) {
        // If the dimensiosn are wrong
        ErrorDialog(
            errorMessage = "Invalid input! Dimensions must be greater than zero.",
            onDismiss = { showDialogInvalidInput = false }
        )
    }


    if (!isLoading && showAccessPointSelectionDialog) {
        AccessPointSelectionDialog(accessPointTypes) { selectedType ->

            showAccessPointSelectionDialog = false
            selectedType?.let { type ->

                val newX = floorplanWidthInPx / 2f


                val newY = floorplanHeightInPx / 2f

                accessPoints.add(AccessPointInstance(type = type, x = newX, y = newY))
            }
        }
    }
}


fun calculateAccessPointPositions(
    floorplanWidth: Float,
    floorplanHeight: Float,
    scale: Float,
    apType: AccessPointType
): List<AccessPointInstance> {
    val accessPointPositions = mutableListOf<AccessPointInstance>()

    // need diamer
    val coverageDiameter = apType.range * scale * 2

    // getting required number of ap hor and ver
    val numAPsHorizontal = ceil(floorplanWidth / coverageDiameter).toInt()
    val numAPsVertical = ceil(floorplanHeight / coverageDiameter).toInt()

    // Calculate the spacing
    val horizontalSpacing = floorplanWidth / numAPsHorizontal
    val verticalSpacing = floorplanHeight / numAPsVertical

    // Place an AP in the center of each coverage rectangle
    for (i in 0 until numAPsHorizontal) {
        for (j in 0 until numAPsVertical) {
            val xPosition = (i * horizontalSpacing) + (horizontalSpacing / 2)
            val yPosition = (j * verticalSpacing) + (verticalSpacing / 2)
            accessPointPositions.add(AccessPointInstance(
                type = apType,
                x = xPosition,
                y = yPosition
            ))
        }
    }

    return accessPointPositions
}



@Composable
fun AccessPointDraggable(
    accessPoint: AccessPointInstance,
    screenScale: Float,
    onUpdate: (AccessPointInstance) -> Unit,
    onDelete: (AccessPointInstance) -> Unit
) {
    var offset by remember { mutableStateOf(Offset(accessPoint.x, accessPoint.y)) }
    var showDelete by remember { mutableStateOf(false) }
    val baseIconSize = 30.dp
    val iconHalfSizePx = with(LocalDensity.current) { (baseIconSize / 2).toPx() }

    LaunchedEffect(key1 = accessPoint) {
        offset = Offset(accessPoint.x , accessPoint.y)
    }

    // Modifier for drag gestures
    val dragGestureModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(onDrag = { change, dragAmount ->
            change.consume()
            offset += dragAmount
        }, onDragEnd = {
            onUpdate(accessPoint.copy(x = offset.x, y = offset.y))
        })
    }

    // need good design here but don't know what
    RangeIndicator(
        center = offset,
        radius = (accessPoint.type.range * screenScale),
    )

    // AP Icon, centered
    Image(
        painter =
        rememberAsyncImagePainter(ImageRequest.Builder(LocalContext.current).data(
            data = accessPoint.type.imageUrl
        ).apply(block = fun ImageRequest.Builder.() {
            crossfade(true)
            error(R.drawable.ic_launcher_background)
        }).build()
        ),
        contentDescription = "Access Point",
        modifier = Modifier
            .offset { IntOffset((offset.x - iconHalfSizePx).roundToInt(), (offset.y - iconHalfSizePx).roundToInt()) }
            .size(baseIconSize)
            .clipToBounds()
            .clickable { showDelete = !showDelete }
            .then(dragGestureModifier)
    )

    // delete button visibility
    if (showDelete) {
        IconButton(
            onClick = {
                onDelete(accessPoint)
                showDelete = false
            },
            modifier = Modifier
                .offset { IntOffset((offset.x - iconHalfSizePx).roundToInt(), (offset.y + iconHalfSizePx).roundToInt()) }
                .size(baseIconSize)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Access Point",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}


@Composable
fun RangeIndicator(
    center: Offset,
    radius: Float,
) {
    val diameter = (radius * 2).dp
    Canvas(modifier = Modifier.size(diameter)) {
        val colors = listOf(
            Color(0xFF00C853),
            Color(0xFF00C853).copy(alpha = 0.6f),
            Color(0x0000C853).copy(alpha = 0.1f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = colors,
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )
    }
}
@Composable
fun ProposalSummaryDialog(summary: ProposalSummary, onClose: () -> Unit) {
    val context = LocalContext.current
    var userChoice by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(1f)
                .fillMaxHeight(1f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "Proposal Summary",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                summary.proposalOptions.forEach { option ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${option.accessPointType.name} x ${option.quantity}", fontWeight = FontWeight.Bold)
                            Text("Rental fee: ¥${option.accessPointType.rentalCost}")
                            Text("Management Fee (monthly): ¥${500 * option.quantity}")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${option.accessPointType.name} x ${option.quantity}", fontWeight = FontWeight.Bold)
                            Text("Purchase cost: ¥${option.accessPointType.purchaseCost}")
                            Text("Management Fee (monthly): ¥${500 * option.quantity}")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val totalDevices = summary.proposalOptions.sumOf { it.quantity }
                val totalMonthlyManagementFee = totalDevices * 500
                val totalRentalMonthly = summary.proposalOptions.sumOf { it.quantity * (it.accessPointType.rentalCost + 500) } + summary.visitationFee
                val totalPurchaseOneTime = summary.proposalOptions.sumOf { it.quantity * it.accessPointType.purchaseCost } + summary.visitationFee
                val totalRentalOneTime = summary.visitationFee
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    // Rental option summary
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rental Option Fees:", fontWeight = FontWeight.Bold)
                        Text("Total One-time Fee: ¥$totalRentalOneTime", fontWeight = FontWeight.Bold)
                        Text("Total Monthly (incl. management fee)): ¥$totalRentalMonthly", fontWeight = FontWeight.Bold)
                    }
                    // Purchase option summary
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Purchase Fees:", fontWeight = FontWeight.Bold)
                        Text("Total One-time (incl. Visitation Fee): ¥$totalPurchaseOneTime", fontWeight = FontWeight.Bold)
                        Text("Total Monthly Fee: ¥$totalMonthlyManagementFee", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { userChoice = "rental" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Choose Rental", maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { userChoice = "purchase" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Choose Purchase", maxLines = 1)
                    }
                }

                // Share button based on choice
                if (userChoice.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val shareText = generateShareContent(summary, userChoice, totalRentalOneTime, totalRentalMonthly, totalPurchaseOneTime, totalMonthlyManagementFee)
                            shareProposal(context, shareText)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Share $userChoice Details")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

private fun generateShareContent(summary: ProposalSummary, choice: String, totalRentalOneTime: Int, totalRentalMonthly: Int, totalPurchaseOneTime: Int, totalMonthlyManagementFee: Int): String {
    val apDetails = summary.proposalOptions.joinToString(separator = "\n") { option ->
        "${option.accessPointType.name} x ${option.quantity} - Rental: ¥${option.accessPointType.rentalCost}, Purchase: ¥${option.accessPointType.purchaseCost}"
    }

    return if (choice == "rental") {
        """
        Rental Option Summary:
        AP Details:
        $apDetails
        
        Total One-time Fee (Visitation Fee): ¥$totalRentalOneTime
        Total Monthly Fee (incl. Management Fee): ¥$totalRentalMonthly
        """.trimIndent()
    } else {
        """
        Purchase Option Summary:
        AP Details:
        $apDetails
        
        Total One-time Fee (incl. Visitation Fee): ¥$totalPurchaseOneTime
        Total Monthly Management Fee: ¥$totalMonthlyManagementFee
        """.trimIndent()
    }
}

private fun shareProposal(context: Context, text: String) {
    val shareIntent = ShareCompat.IntentBuilder(context)
        .setType("text/plain")
        .setText(text)
        .createChooserIntent()
        .apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
    context.startActivity(shareIntent)
}




fun generateProposalOptions(accessPoints: List<AccessPointInstance>): List<ProposalOption> {
    val groupedByType = accessPoints.groupBy { it.type }
    return groupedByType.map { (type, instances) ->
        ProposalOption(
            accessPointType = type,
            quantity = instances.size,
            rentalTotalCost = instances.size * type.rentalCost,
            purchaseTotalCost = instances.size * type.purchaseCost
        )
    }
}


@Composable
fun RequestDimensionsDialog(onDimensionsReady: (Float, Float) -> Unit) {
    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        //Learned today
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Enter Floorplan Dimensions") },
            text = {
                Column {
                    TextField(
                        value = width,
                        onValueChange = { width = it },
                        label = { Text("Width in meters") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    TextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Height in meters") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        onDimensionsReady(width.toFloatOrNull() ?: 0f, height.toFloatOrNull() ?: 0f)
                    }
                ) { Text("Confirm") }
            }
        )
    }
}


@Composable
fun AccessPointSelectionDialog(
    accessPointTypes: List<AccessPointType>,
    onSelection: (AccessPointType?) -> Unit
) {
    var selectedTypeIndex by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = { onSelection(null) }) {
        Surface(
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn {
                    itemsIndexed(accessPointTypes) { index, type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTypeIndex = index }
                                .padding(16.dp)
                        ) {
                            Text(type.name, modifier = Modifier.weight(1f))
                            if (selectedTypeIndex == index) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected")
                            }
                        }
                    }
                }


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onSelection(null) }
                    ) { Text("Cancel") }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelection(selectedTypeIndex?.let { accessPointTypes.getOrNull(it) })
                        }
                    ) { Text("Confirm") }
                }
            }
        }
    }
}



@Composable
fun ErrorDialog(errorMessage: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Error") },
        text = { Text(text = errorMessage) },
        confirmButton = {
            Button(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ScreenPreview()
{
    FloorplanPickerAndDisplay()
}