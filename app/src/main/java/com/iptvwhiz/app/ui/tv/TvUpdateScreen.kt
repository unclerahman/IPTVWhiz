package com.iptvwhiz.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.ui.common.UpdateBody
import com.iptvwhiz.app.ui.state.UpdateViewModel
import com.iptvwhiz.app.ui.state.appViewModel
import com.iptvwhiz.app.ui.theme.Brand
import com.iptvwhiz.app.ui.theme.TvSafeArea

/** Settings → Check for updates on TV. */
@Composable
fun TvUpdateScreen() {
    val vm = appViewModel { container, _ -> UpdateViewModel(container) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvSafeArea.Horizontal, vertical = TvSafeArea.Vertical),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.headlineMedium)
        // The shared body speaks mobile MaterialTheme, which is absent on TV — pass the brand
        // colors directly: light text, brand buttons, D-pad focus on the one active button.
        UpdateBody(
            vm,
            accent = Brand.Primary,
            onAccent = Brand.OnPrimary,
            textColor = Brand.DarkOnSurface,
            focusButtons = true,
        )
    }
}
