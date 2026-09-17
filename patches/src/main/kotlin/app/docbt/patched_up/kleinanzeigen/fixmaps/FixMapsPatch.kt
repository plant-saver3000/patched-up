// Based on [Fix] Restore location maps via MicroG-RE by andrewliang25
// https://github.com/andrewliang25/morphe-patches
// Copyright (C) 2026 andrewliang25
// Licensed under GNU General Public License v3.0
// Adapted for Kleinanzeigen (com.ebay.kleinanzeigen) by plant-saver3000

package app.docbt.patched_up.kleinanzeigen.fixmaps
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val MICROG_PACKAGE = "app.revanced.android.gms"
private const val EXTENSION_CLASS = "Lapp/docbt/patched_up/extension/LocationMaps;"

private val COMPAT = Compatibility(
    name = "Kleinanzeigen",
    packageName = "com.ebay.kleinanzeigen",
    appIconColor = 0x2EAD33,
    targets = listOf(AppTarget(version = "2026.38.3")),
)

// Adds MicroG-RE to AndroidManifest.xml <queries> so Android 11+
// package visibility rules allow createPackageContext() to find it.
private val fixMapsManifestPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.getElementsByTagName("manifest").item(0)

            fun Node.hasChild(tag: String, name: String) =
                (0 until childNodes.length)
                    .map { childNodes.item(it) }
                    .any { it is Element && it.tagName == tag && it.getAttribute("android:name") == name }

            val queries = (0 until manifest.childNodes.length)
                .map { manifest.childNodes.item(it) }
                .firstOrNull { it is Element && (it as Element).tagName == "queries" }
                ?: manifest.appendChild(document.createElement("queries"))

            if (!queries.hasChild("package", MICROG_PACKAGE)) {
                queries.appendChild(
                    document.createElement("package").apply {
                        setAttribute("android:name", MICROG_PACKAGE)
                    },
                )
            }
        }
    }
}

@Suppress("unused")
val fixMapsPatch = bytecodePatch(
    name = "[Fix] Restore Maps via MicroG-RE",
    description = "Restores the item location map on re-signed builds by redirecting the " +
        "Maps renderer to MicroG-RE's bundled MapLibre renderer, which validates no API key. " +
        "Requires MicroG-RE 7.0.0 or later. Root Mount installs do not need this patch.",
    default = true,
) {
    compatibleWith(COMPAT)
    dependsOn(fixMapsManifestPatch)
    extendWith("extensions/extension.mpe")

    execute {
        // Returns null when MicroG-RE is absent/too old, so the patch is a
        // no-op on those devices — LINE falls through to Play Services.
        MapsModuleContextFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-static { p0 }, $EXTENSION_CLASS->getMapsContext(Landroid/content/Context;)Landroid/content/Context;
                move-result-object v0
                if-eqz v0, :original
                return-object v0
            """,
            ExternalLabel("original", MapsModuleContextFingerprint.method.getInstruction(0)),
        )
    }
}
