// SPDX-License-Identifier: GPL-3.0-only

package com.openrecorder.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object RecorderIcons {
    val Back: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        buildIcon("Back") {
            moveTo(20f, 11f)
            lineTo(7.83f, 11f)
            lineTo(13.42f, 5.41f)
            lineTo(12f, 4f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            lineTo(13.42f, 18.59f)
            lineTo(7.83f, 13f)
            lineTo(20f, 13f)
            close()
        }
    }

    val Play: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        buildIcon("Play") {
            moveTo(8f, 5f)
            lineTo(19f, 12f)
            lineTo(8f, 19f)
            close()
        }
    }

    val Settings: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(19.43f, 12.98f)
                curveTo(19.47f, 12.66f, 19.5f, 12.33f, 19.5f, 12f)
                curveTo(19.5f, 11.67f, 19.48f, 11.34f, 19.43f, 11.02f)
                lineTo(21.54f, 9.37f)
                curveTo(21.73f, 9.22f, 21.78f, 8.95f, 21.66f, 8.73f)
                lineTo(19.66f, 5.27f)
                curveTo(19.54f, 5.05f, 19.29f, 4.96f, 19.06f, 5.05f)
                lineTo(16.57f, 6.05f)
                curveTo(16.05f, 5.65f, 15.49f, 5.32f, 14.88f, 5.07f)
                lineTo(14.5f, 2.42f)
                curveTo(14.47f, 2.18f, 14.25f, 2f, 14f, 2f)
                lineTo(10f, 2f)
                curveTo(9.75f, 2f, 9.54f, 2.18f, 9.51f, 2.42f)
                lineTo(9.13f, 5.07f)
                curveTo(8.52f, 5.32f, 7.95f, 5.66f, 7.44f, 6.05f)
                lineTo(4.95f, 5.05f)
                curveTo(4.72f, 4.97f, 4.47f, 5.05f, 4.35f, 5.27f)
                lineTo(2.35f, 8.73f)
                curveTo(2.22f, 8.95f, 2.28f, 9.22f, 2.47f, 9.37f)
                lineTo(4.58f, 11.02f)
                curveTo(4.54f, 11.34f, 4.51f, 11.68f, 4.51f, 12f)
                curveTo(4.51f, 12.32f, 4.53f, 12.66f, 4.58f, 12.98f)
                lineTo(2.47f, 14.63f)
                curveTo(2.28f, 14.78f, 2.23f, 15.05f, 2.35f, 15.27f)
                lineTo(4.35f, 18.73f)
                curveTo(4.47f, 18.95f, 4.72f, 19.04f, 4.95f, 18.95f)
                lineTo(7.44f, 17.95f)
                curveTo(7.96f, 18.35f, 8.52f, 18.68f, 9.13f, 18.93f)
                lineTo(9.51f, 21.58f)
                curveTo(9.54f, 21.82f, 9.75f, 22f, 10f, 22f)
                lineTo(14f, 22f)
                curveTo(14.25f, 22f, 14.46f, 21.82f, 14.49f, 21.58f)
                lineTo(14.87f, 18.93f)
                curveTo(15.48f, 18.68f, 16.05f, 18.35f, 16.56f, 17.95f)
                lineTo(19.05f, 18.95f)
                curveTo(19.28f, 19.03f, 19.53f, 18.95f, 19.65f, 18.73f)
                lineTo(21.65f, 15.27f)
                curveTo(21.77f, 15.05f, 21.72f, 14.78f, 21.53f, 14.63f)
                lineTo(19.42f, 12.98f)
                close()

                moveTo(12f, 8.5f)
                curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
                curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f)
                curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
                curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f)
                close()
            }
        }.build()
    }

    val Recordings: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        buildIcon("Recordings") {
            moveTo(3f, 4f)
            lineTo(21f, 4f)
            lineTo(21f, 6f)
            lineTo(3f, 6f)
            close()

            moveTo(3f, 18f)
            lineTo(21f, 18f)
            lineTo(21f, 20f)
            lineTo(3f, 20f)
            close()

            moveTo(3f, 6f)
            lineTo(5f, 6f)
            lineTo(5f, 18f)
            lineTo(3f, 18f)
            close()

            moveTo(19f, 6f)
            lineTo(21f, 6f)
            lineTo(21f, 18f)
            lineTo(19f, 18f)
            close()

            moveTo(10f, 8f)
            lineTo(16f, 12f)
            lineTo(10f, 16f)
            close()
        }
    }

    val Check: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        buildIcon("Check") {
            moveTo(9.2f, 16.6f)
            lineTo(4.6f, 12f)
            lineTo(6f, 10.6f)
            lineTo(9.2f, 13.8f)
            lineTo(18f, 5f)
            lineTo(19.4f, 6.4f)
            close()
        }
    }

    val SelectAll: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        buildMultiSelectIcon(name = "SelectAll", selected = false)
    }

    val AllSelected: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        buildMultiSelectIcon(name = "AllSelected", selected = true)
    }

    val Delete: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        buildIcon("Delete") {
            moveTo(5f, 5f)
            lineTo(19f, 5f)
            lineTo(19f, 7f)
            lineTo(5f, 7f)
            close()

            moveTo(9f, 3f)
            lineTo(15f, 3f)
            lineTo(15f, 5f)
            lineTo(9f, 5f)
            close()

            moveTo(7f, 7f)
            lineTo(9f, 7f)
            lineTo(9f, 19f)
            lineTo(15f, 19f)
            lineTo(15f, 7f)
            lineTo(17f, 7f)
            lineTo(17f, 19f)
            curveTo(17f, 20.1f, 16.1f, 21f, 15f, 21f)
            lineTo(9f, 21f)
            curveTo(7.9f, 21f, 7f, 20.1f, 7f, 19f)
            close()
        }
    }

    val Close: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        buildIcon("Close") {
            moveTo(6.4f, 5f)
            lineTo(12f, 10.6f)
            lineTo(17.6f, 5f)
            lineTo(19f, 6.4f)
            lineTo(13.4f, 12f)
            lineTo(19f, 17.6f)
            lineTo(17.6f, 19f)
            lineTo(12f, 13.4f)
            lineTo(6.4f, 19f)
            lineTo(5f, 17.6f)
            lineTo(10.6f, 12f)
            lineTo(5f, 6.4f)
            close()
        }
    }

    val Sort: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        buildIcon("Sort") {
            moveTo(3f, 6f)
            lineTo(21f, 6f)
            lineTo(21f, 8f)
            lineTo(3f, 8f)
            close()

            moveTo(3f, 11f)
            lineTo(15f, 11f)
            lineTo(15f, 13f)
            lineTo(3f, 13f)
            close()

            moveTo(3f, 16f)
            lineTo(9f, 16f)
            lineTo(9f, 18f)
            lineTo(3f, 18f)
            close()
        }
    }

    private fun buildMultiSelectIcon(
        name: String,
        selected: Boolean,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 2f)
            lineTo(16f, 2f)
            curveTo(18.21f, 2f, 20f, 3.79f, 20f, 6f)
            lineTo(20f, 7f)
            lineTo(18f, 7f)
            lineTo(18f, 6f)
            curveTo(18f, 4.9f, 17.1f, 4f, 16f, 4f)
            lineTo(6f, 4f)
            curveTo(4.9f, 4f, 4f, 4.9f, 4f, 6f)
            lineTo(4f, 16f)
            curveTo(4f, 17.1f, 4.9f, 18f, 6f, 18f)
            lineTo(7f, 18f)
            lineTo(7f, 20f)
            lineTo(6f, 20f)
            curveTo(3.79f, 20f, 2f, 18.21f, 2f, 16f)
            lineTo(2f, 6f)
            curveTo(2f, 3.79f, 3.79f, 2f, 6f, 2f)
            close()
        }

        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        ) {
            moveTo(10f, 6f)
            lineTo(18f, 6f)
            curveTo(20.21f, 6f, 22f, 7.79f, 22f, 10f)
            lineTo(22f, 18f)
            curveTo(22f, 20.21f, 20.21f, 22f, 18f, 22f)
            lineTo(10f, 22f)
            curveTo(7.79f, 22f, 6f, 20.21f, 6f, 18f)
            lineTo(6f, 10f)
            curveTo(6f, 7.79f, 7.79f, 6f, 10f, 6f)
            close()

            if (selected) {
                moveTo(12.5f, 17.5f)
                lineTo(9f, 14f)
                lineTo(10.5f, 12.5f)
                lineTo(12.5f, 14.5f)
                lineTo(17.5f, 9.5f)
                lineTo(19f, 11f)
                close()
            } else {
                moveTo(10f, 8f)
                curveTo(8.9f, 8f, 8f, 8.9f, 8f, 10f)
                lineTo(8f, 18f)
                curveTo(8f, 19.1f, 8.9f, 20f, 10f, 20f)
                lineTo(18f, 20f)
                curveTo(19.1f, 20f, 20f, 19.1f, 20f, 18f)
                lineTo(20f, 10f)
                curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f)
                close()
            }
        }
    }.build()

    private fun buildIcon(
        name: String,
        pathBuilder: PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            pathBuilder()
        }
    }.build()
}
