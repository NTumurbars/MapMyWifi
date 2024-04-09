package com.example.mapmywifi
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
import androidx.compose.ui.res.painterResource
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import com.example.mapmywifi.ui.theme.MapMyWifiTheme
import com.google.accompanist.coil.rememberImagePainter
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
    LaunchedEffect(Unit) {
        isLoading = true
        coroutineScope.launch {
            accessPointTypes = AccessPointTypes.fetchAccessPointTypes() ?: emptyList()
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


    var boxSize by remember { mutableStateOf(IntSize.Zero) }


    val screenScale = remember(boxSize, floorplanWidthInMeters) {
        floorplanWidthInMeters?.let { widthInMeters ->
            if (widthInMeters > 0) boxSize.width.toFloat() / widthInMeters else 1f
        } ?: 1f
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
                    screenScale = screenScale,
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

                // Use first AccessPointType for auto-placement
                val autoPlacedAPs = calculateAccessPointPositions(
                    width = width,
                    height = height,
                    scale = screenScale,
                    apType = accessPointTypes.first()
                )

                accessPoints.clear()
                accessPoints.addAll(autoPlacedAPs)
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


                // box width is fine since it fits to it
                val newX = boxSize.width / 2f

                // need a scale factor to know the height of the floorplan displayed inside the box

                val scaleFactor = floorplanBitmap?.let { bitmap ->
                    boxSize.width.toFloat() / bitmap.width.toFloat()
                } ?: 1f // Default scale factor if floorplanBitmap is null.

                val newY = floorplanBitmap?.let { bitmap ->
                    (bitmap.height.toFloat() * scaleFactor) / 2f
                } ?: 0f

                accessPoints.add(AccessPointInstance(type = type, x = newX, y = newY))
            }
        }
    }
}

fun calculateAccessPointPositions(
    width: Float,
    height: Float,
    scale: Float,
    apType: AccessPointType
): SnapshotStateList<AccessPointInstance> {
    val positions = mutableStateListOf<AccessPointInstance>()
    val range = apType.range



    val requiredNumberHorizontal = ceil(width/range).toInt()
    val requiredNumberVertical = ceil(height/range).toInt()


    for(i in 1..requiredNumberHorizontal)
    {
        for(j in 1..requiredNumberVertical)
        {
            val x = width/i * scale
            val y = height/j * scale
            positions.add(AccessPointInstance(apType, x = x, y = y))
        }

    }

    return positions
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
    val iconSize = baseIconSize
    val iconHalfSizePx = with(LocalDensity.current) { (iconSize / 2).toPx() }

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
        painter = rememberImagePainter(
            data = accessPoint.type.imageUrl, // Update this to the imageUrl from your AccessPointType
            builder = {
                crossfade(true)
                error(R.drawable.placeholder) // Replace with your placeholder drawable
            }
        ),
        contentDescription = "Access Point",
        modifier = Modifier
            .offset { IntOffset((offset.x - iconHalfSizePx).roundToInt(), (offset.y - iconHalfSizePx).roundToInt()) }
            .size(iconSize)
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
                .size(iconSize)
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
    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f)
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

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Rental Option",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Purchase Option",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

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
                val totalMonthlyPurchase = totalMonthlyManagementFee

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    // Rental option summary
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rental Option Fees:", fontWeight = FontWeight.Bold)
                        Text("Total One-time Fee: ¥$totalPurchaseOneTime", fontWeight = FontWeight.Bold)
                        Text("Total Monthly (incl. management fee)): ¥$totalRentalMonthly", fontWeight = FontWeight.Bold)
                    }
                    // Purchase option summary
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Purchase Fees:", fontWeight = FontWeight.Bold)
                        Text("Total One-time (incl. Visitation Fee): ¥$totalPurchaseOneTime", fontWeight = FontWeight.Bold)
                        Text("Total Monthly Fee: ¥$totalMonthlyPurchase", fontWeight = FontWeight.Bold)
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

