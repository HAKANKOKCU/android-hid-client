package me.arianb.usb_hid_client.hid_utils

import android.app.Application
import android.content.res.Resources
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.shell_utils.RootState
import timber.log.Timber
import java.io.File

class CharacterDeviceManager(private val application: Application) {
    private val dispatcher = Dispatchers.IO

    suspend fun createCharacterDevices(): Boolean {
        val appResources: Resources = application.resources

        return withContext(dispatcher) {
            if (!Shell.getShell().isRoot) {
                return@withContext false
            }

            val commandResult = Shell.cmd(appResources.openRawResource(R.raw.create_char_devices)).exec()
            Timber.d("create device script: \nstdout=%s\nstderr=%s", commandResult.out, commandResult.err)

            fixSelinuxPermissions()

            try {
                withTimeout(5000) {
                    for (devicePath in ALL_CHARACTER_DEVICE_PATHS) {
                        launch {
                            // wait until the device file exists before trying to fix its permissions
                            while (!File(devicePath).exists()) {
                                Timber.d("$devicePath doesn't exist yet, sleeping for a bit before trying again...")
                                delay(200)
                            }
                            Timber.d("$devicePath exists now!!!")
                            fixCharacterDevicePermissions(devicePath)
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Timber.e("Shell script ran, but we timed out while waiting for character devices to be created.")
                // FIXME: show this error to the user
            }

            return@withContext true
        }
    }

    private fun fixSelinuxPermissions() {
        val selinuxPolicyCommand = "${RootState.getSepolicyCommand()} '$SELINUX_POLICY'"
        Shell.cmd(selinuxPolicyCommand).exec()
    }

    fun fixCharacterDevicePermissions(device: String): Boolean {
        val appUID: Int = application.applicationInfo.uid

        if (!Shell.getShell().isRoot) {
            return false
        }

        // Set Linux permissions -> only my app user can r/w to the char device
        val chownCommand = "chown ${appUID}:${appUID} $device"
        val chmodCommand = "chmod 600 $device"
        Shell.cmd(chownCommand).exec()
        Shell.cmd(chmodCommand).exec()

        // Set SELinux permissions -> only my app's selinux context can r/w to the char device
        val chconCommand = "chcon u:object_r:device:s0:${getSelinuxCategories()} $device"
        Shell.cmd(chconCommand).exec()

        return true
    }

    private fun getSelinuxCategories(): String {
        val appDataDirPath: String = application.applicationInfo.dataDir

        // Get selinux context for app
        val commandResult = Shell.cmd("stat -c %C $appDataDirPath").exec()

        // Get the part of the context that I need (categories) by grabbing everything after the last ':'
        val contextFromCommand = java.lang.String.join("\n", commandResult.out)
        var categories = contextFromCommand

        // TODO: handle the case that stdout doesn't include any ':' (idk why that would even happen tho)
        categories = categories.substring(categories.lastIndexOf(':') + 1)
        categories = categories.trim { it <= ' ' } // trim whitespace
        Timber.d("context (before,after): (%s,%s)", contextFromCommand, categories)

        // If it hasn't changed, then the previous piece of code failed to get the substring
        if (categories == contextFromCommand) {
            Timber.e("Failed to get app's selinux context")
        }
        return categories
    }

    fun deleteCharacterDevices(): Boolean {
        val appResources: Resources = application.resources

        if (!Shell.getShell().isRoot) {
            return false
        }

        val commandResult = Shell.cmd(appResources.openRawResource(R.raw.delete_char_devices)).exec()
        Timber.d("delete device script: \nstdout=%s\nstderr=%s", commandResult.out, commandResult.err)

        return true
    }

    companion object {
        // character device paths
        const val KEYBOARD_DEVICE_PATH = "/dev/hidg0"
        const val MOUSE_DEVICE_PATH = "/dev/hidg1"
        private val ALL_CHARACTER_DEVICE_PATHS = listOf(KEYBOARD_DEVICE_PATH, MOUSE_DEVICE_PATH)

        // SeLinux stuff
        private const val SELINUX_DOMAIN = "appdomain"
        private const val SELINUX_POLICY = "allow $SELINUX_DOMAIN device chr_file { getattr open write }"

        fun characterDeviceMissing(charDevicePath: String): Boolean {
            return if (!ALL_CHARACTER_DEVICE_PATHS.contains(charDevicePath)) {
                true
            } else !File(charDevicePath).exists()
        }

        fun anyCharacterDeviceMissing(): Boolean {
            for (charDevicePath in ALL_CHARACTER_DEVICE_PATHS) {
                if (!File(charDevicePath).exists()) {
                    return true
                }
            }
            return false
        }
    }
}