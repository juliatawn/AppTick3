package com.juliacai.apptick

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.juliacai.apptick.deviceApps.AppListViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class AppSelectFragment : Fragment() {

    private lateinit var settings: AppLimitSettings
    private val viewModel: AppListViewModel by viewModels()
    private val selectedApps = MutableStateFlow<List<AppInfo>>(emptyList())

    override fun onAttach(context: Context) {
        super.onAttach(context)
        settings = context as? AppLimitSettings
            ?: throw RuntimeException("$context must implement AppLimitSettings")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val apps by viewModel.apps.observeAsState(emptyList())
                AppSelectScreen(
                    apps = apps,
                    selectedApps = selectedApps,
                    onAppSelected = { app, isSelected ->
                        val currentSelected = selectedApps.value.toMutableList()
                        if (isSelected) {
                            currentSelected.add(app)
                        } else {
                            currentSelected.removeAll { it.appPackage == app.appPackage }
                        }
                        selectedApps.value = currentSelected
                        settings.selected = currentSelected
                    },
                    onNextClick = { settings.setWhichPage(1) },
                    searchQuery = "", // Add state for search query
                    onSearchQueryChange = { /* Implement search logic */ }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadInstalledApps()
        selectedApps.value = settings.selected
    }
}
