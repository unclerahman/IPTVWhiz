package com.iptvwhiz.app.ui.mobile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptvwhiz.app.R
import com.iptvwhiz.app.ui.common.UpdateBody
import com.iptvwhiz.app.ui.state.UpdateViewModel
import com.iptvwhiz.app.ui.state.appViewModel

/** Settings → Check for updates on phones. */
@Composable
fun MobileUpdateScreen(onBack: () -> Unit) {
    val vm = appViewModel { container, _ -> UpdateViewModel(container) }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.update_title), onBack) }) { padding ->
        UpdateBody(vm, Modifier.padding(padding).padding(16.dp))
    }
}
