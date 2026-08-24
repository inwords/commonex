package ru.commonex

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class AppFunctionRegistrationTest {

    @Test
    fun generatedServiceAndMetadata_areRegistered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val component = ComponentName(
            context,
            "com.inwords.expenses.integration.base.appfunctions.CommonExAppFunctionService",
        )

        @Suppress("DEPRECATION")
        val serviceInfo = packageManager.getServiceInfo(component, 0)

        if (Build.VERSION.SDK_INT >= 36) {
            val serviceClass = Class.forName(component.className)
            assertTrue(android.app.Service::class.java.isAssignableFrom(serviceClass))
        }
        assertTrue(serviceInfo.exported)
        assertEquals("android.permission.BIND_APP_FUNCTION_SERVICE", serviceInfo.permission)
        assertEquals(
            "app_functions_schema.xsd",
            packageManager.getProperty("android.app.appfunctions.schema", component).string,
        )
        assertEquals(
            "commonex_app_function_service.xml",
            packageManager.getProperty("android.app.appfunctions.v2", component).string,
        )
        context.assets.open("commonex_app_function_service.xml").use { schema ->
            assertTrue(schema.read() != -1)
        }

        val matchingServices = packageManager.queryIntentServices(
            Intent("android.app.appfunctions.AppFunctionService").setPackage(context.packageName),
            PackageManager.MATCH_ALL,
        )
        assertTrue(matchingServices.any { resolveInfo -> resolveInfo.serviceInfo.name == component.className })

        val metadataResource = packageManager
            .getProperty("android.app.appfunctions.app_metadata", context.packageName)
            .resourceId
        assertTrue(metadataResource != 0)

        context.resources.getXml(metadataResource).use { parser ->
            while (parser.eventType != XmlPullParser.START_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) {
                parser.next()
            }
            assertEquals("AppFunctionAppMetadata", parser.name)
            assertTrue(
                parser.getAttributeValue(APP_FUNCTION_NAMESPACE, "description").contains("listCurrencies"),
            )
            val displayDescription = parser.getAttributeResourceValue(
                APP_FUNCTION_NAMESPACE,
                "displayDescription",
                0,
            )
            assertEquals(
                context.getString(R.string.app_functions_display_description),
                context.getString(displayDescription),
            )
        }
    }

    private companion object {
        const val APP_FUNCTION_NAMESPACE = "http://schemas.android.com/apk/androidx.appfunctions"
    }
}
