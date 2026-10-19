package cl.nheox.quests.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/**
 * First run.
 *
 * Four cards, and the second one is the only one that really matters: it
 * explains why fear is rated separately, which is the single idea that makes
 * this app different from a to-do list. Someone who skips that explanation will
 * rate everything "none" and never find out.
 */
@Composable
public fun OnboardingOverlay(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = strings
    var page by remember { mutableStateOf(0) }

    val pages = listOf(
        Triple(Glyph.TARGET, text.onboardingWelcome, text.onboardingBody),
        Triple(Glyph.FLAME, text.onboardingFearTitle, text.onboardingFearBody),
        Triple(Glyph.FOLDER, text.onboardingFilesTitle, text.onboardingFilesBody),
        Triple(Glyph.COMMAND, text.onboardingKeyboardTitle, text.onboardingKeyboardBody),
    )
    val (glyph, title, body) = pages[page]

    Box(
        modifier
            .fillMaxSize()
            .background(theme.background.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .background(theme.card, RoundedCornerShape(Radii.lg))
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QuestIcon(glyph, size = 44.dp, tint = theme.progression)
            Spacer(Modifier.height(Space.lg))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = theme.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.md))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xl))

            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(if (index == page) 8.dp else 6.dp)
                            .background(
                                if (index == page) theme.progression else theme.border,
                                RoundedCornerShape(Radii.pill),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(Space.lg))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuestButton(text.actionSkip, compact = true, onClick = onFinish)
                if (page == pages.lastIndex) {
                    QuestButton(text.onboardingStart, primary = true, onClick = onFinish)
                } else {
                    QuestButton(text.actionNext, primary = true, onClick = { page++ })
                }
            }
        }
    }
}
