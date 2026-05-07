package com.crispasr.androidspeech;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.crispasr.androidspeech.core.ModelCatalog;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DeviceModelCatalogTest {
    @Test
    public void createsDeviceModelDirectory() {
        File dir = ModelCatalog.appModelDirectory(InstrumentationRegistry.getInstrumentation().getTargetContext());
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
    }
}
