    settings: AppSettings,
    historyStore: HistoryStore,
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        val modelState by modelManager.state.collectAsState()
        val visionState by visionModelManager.state.collectAsState()
        val history by historyStore.entries.collectAsState()
        var screen by remember { mutableStateOf(Screen.Solve) }

        // The system back gesture leaves sub-screens before exiting the app.
        PlatformBackHandler(enabled = screen != Screen.Solve) { screen = Screen.Solve }

        // Once the vision model finishes installing, return to solving.
        LaunchedEffect(visionState) {
            if (screen == Screen.VisionSetup && visionState is ModelState.Available) {
                screen = Screen.Solve
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (screen == Screen.Solve) {
                                AppLogo(Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                when (screen) {
                                    Screen.Solve -> "LocalMathy"
                                    Screen.Settings -> "Settings"
                                    Screen.History -> "History"
                                    Screen.VisionSetup -> "Photo solving"
                                },
                            )
                        }
                    },
                    navigationIcon = {
                        if (screen != Screen.Solve) {
                            IconButton(onClick = { screen = Screen.Solve }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        }
                    },
                    actions = {
                        if (screen == Screen.Solve) {
                            OverflowMenu(
                                onHistory = { screen = Screen.History },
                                onSettings = { screen = Screen.Settings },
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                when (screen) {
                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        historyCount = history.size,
                        modelState = modelState,
                        visionModelState = visionState,
                        onClearHistory = historyStore::clear,
                        onDeleteModel = {
                            engine.unload()
                            modelManager.deleteModel()
                            screen = Screen.Solve
                        },
                        onDeleteVisionModel = {
                            transcriber?.unload()
                            visionModelManager.deleteModel()
                        },
                    )

                    Screen.History -> HistoryScreen(
                        entries = history,
                        onDelete = historyStore::delete,
                    )

                    Screen.VisionSetup -> ModelSetupScreen(
                        modelManager = visionModelManager,
                        state = visionState,
                        intro = "Photo solving reads your photo with ${ModelInfo.Vision.name}, an " +
                            "optional on-device vision model (about ${formatBytes(ModelInfo.Vision.approxSizeBytes)}). " +
                            "Typed questions don't need it.",
                    )

                    Screen.Solve -> when (modelState) {
                        is ModelState.Available -> {
                            SolveScreen(
                                engine = engine,
                                state = solveState,
                                settings = settings,
                                imagePicker = imagePicker,
                                visionModelInstalled = visionState is ModelState.Available,
                                onSetUpVisionModel = { screen = Screen.VisionSetup },
                            )
                            // One-time explainer for the optional photo feature.
                            if (imagePicker?.isSupported == true && !settings.hasSeenPhotoIntro &&
                                !solveState.isHandlingImage
                            ) {
                                PhotoIntroDialog(onDismiss = { settings.hasSeenPhotoIntro = true })
                            }
                        }
                        else -> ModelSetupScreen(modelManager, modelState)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoIntroDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New: solve from a photo") },
        text = {
            Text(
                "LocalMathy solves typed math offline out of the box. You can also snap a photo " +
                    "of a problem — crop it, and it's read into an editable question you can tweak " +
                    "before solving. Photo solving uses an optional extra model " +
                    "(${ModelInfo.Vision.name}, ~${formatBytes(ModelInfo.Vision.approxSizeBytes)}) " +
                    "you can download or import when you first use it.",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

@Composable
private fun OverflowMenu(onHistory: () -> Unit, onSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("History") },
            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
            onClick = {
                expanded = false
                onHistory()
            },
        )
        DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = {
                expanded = false
                onSettings()
            },
        )
    }
}
