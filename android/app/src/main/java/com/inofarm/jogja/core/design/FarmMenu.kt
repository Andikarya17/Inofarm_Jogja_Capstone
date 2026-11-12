package com.inofarm.jogja.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Extra room at the end of every menu list so a floating button never hides the last row. */
internal val LocalFarmListBottomInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

@Composable
internal fun FarmMenuList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium),
        contentPadding = PaddingValues(top = FarmSpace.Medium, bottom = FarmSpace.Large + LocalFarmListBottomInset.current),
        content = content
    )
}

@Composable
internal fun FarmMenuHeader(
    title: String,
    description: String,
    contextual: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val body: @Composable ColumnScope.() -> Unit = {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
    if (!contextual) {
        Column(Modifier.fillMaxWidth().padding(vertical = FarmSpace.Tiny),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Small), content = body)
        return
    }
    MaterialTheme(colorScheme = ForestScheme) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = FarmShape.Card,
            color = ForestScheme.background,
            contentColor = ForestScheme.onBackground
        ) {
            Column(
                Modifier.padding(horizontal = FarmSpace.Large, vertical = FarmSpace.Medium),
                verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny), content = body
            )
        }
    }
}

@Composable
internal fun FarmRecordCard(
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val border = BorderStroke(1.dp, colors.outlineVariant)
    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Large, vertical = FarmSpace.Medium),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Small),
            content = content
        )
    }
    if (onOpen == null) {
        Surface(modifier.fillMaxWidth(), FarmShape.Card, colors.surface, border = border, content = body)
    } else {
        Surface(onOpen, modifier.fillMaxWidth(), shape = FarmShape.Card, color = colors.surface, border = border, content = body)
    }
}
