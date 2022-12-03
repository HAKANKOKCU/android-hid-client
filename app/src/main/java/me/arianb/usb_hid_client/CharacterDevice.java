package me.arianb.usb_hid_client;

import static me.arianb.usb_hid_client.ProcessStreamHelper.getProcessStdError;
import static me.arianb.usb_hid_client.ProcessStreamHelper.getProcessStdOutput;

import android.content.Context;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import timber.log.Timber;

public class CharacterDevice {
	private final Context appContext;
	private final int appUID;

	public CharacterDevice(Context context) {
		this.appContext = context;
		appUID = context.getApplicationInfo().uid;
	}

	// TODO: add more error handling
	public boolean createCharacterDevice() {
		final String SCRIPT_FILENAME = "create_char_device.sh";
		final String scriptPath = appContext.getFilesDir().getPath() + "/" + SCRIPT_FILENAME;

		// Copying over script every time instead of doing a check for its existence because
		// this way allows me to update the script without having to do an existence check + diff
		try {
			InputStream ins = appContext.getResources().openRawResource(R.raw.create_char_device);
			byte[] buffer = new byte[ins.available()];
			ins.read(buffer);
			ins.close();
			FileOutputStream fos = appContext.openFileOutput(SCRIPT_FILENAME, Context.MODE_PRIVATE);
			fos.write(buffer);
			fos.close();
		} catch (IOException e) {
			Timber.e(Log.getStackTraceString(e));
		}
		File file = appContext.getFileStreamPath(SCRIPT_FILENAME);
		file.setExecutable(true);

		try {
			Process createCharDeviceShell = Runtime.getRuntime().exec("su");
			DataOutputStream createCharDeviceOS = new DataOutputStream(createCharDeviceShell.getOutputStream());
			createCharDeviceOS.writeBytes(scriptPath + "\n");
			createCharDeviceOS.flush();
			createCharDeviceOS.writeBytes("exit" + "\n");
			createCharDeviceOS.flush();
			Timber.d("create dev script: stdout=%s,stderr=%s", getProcessStdOutput(createCharDeviceShell), getProcessStdError(createCharDeviceShell));
			// TODO: process timeout + check return code
		} catch (IOException e) {
			Timber.e(Log.getStackTraceString(e));
		}

		return true;
	}

	// TODO: add more error handling
	public boolean fixCharacterDevicePermissions(String device) {
		try {
			// Get selinux context for app
			String context;
			final String appDataDir = appContext.getDataDir().getPath(); // ex: /data/data/me.arianb.usb_hid_client
			Process getContextShell = Runtime.getRuntime().exec("su");
			DataOutputStream getContextOS = new DataOutputStream(getContextShell.getOutputStream());
			getContextOS.writeBytes("stat -c %C " + appDataDir + "\n");
			getContextOS.flush();
			getContextOS.writeBytes("exit" + "\n");
			getContextOS.flush();

			// Get the part of the context that I need
			String getContextShellOutput = getProcessStdOutput(getContextShell);
			context = getContextShellOutput;
			//Timber.d("context: %s", context);
			for (int i = 0; i < context.length(); i++) {
				if (context.charAt(i) == ':' && context.substring(i).length() >= 3) { // If current char is : && there's at least 2 more chars
					if (context.startsWith(":s0", i)) {
						context = context.substring(i, context.length() - 1); // Trims off last char (newline)
						break;
					}
				}
			}
			//Timber.d("context (fixed): %s", context);

			// If it hasn't changed, then the previous piece of code failed to get the substring
			if (context.equals(getContextShellOutput)) {
				Timber.e("Failed to get app's selinux context");
			}
			//Timber.d("process output: stdout=%s,stderr=%s", getContextShellOutput, getProcessStdError(getContextShell));

			Process fixPermsShell = Runtime.getRuntime().exec("su");
			DataOutputStream fixPermsOS = new DataOutputStream(fixPermsShell.getOutputStream());
			fixPermsOS.writeBytes("chown " + appUID + ":" + appUID + " /dev/hidg0" + "\n");
			fixPermsOS.flush();
			fixPermsOS.writeBytes("chmod 600 /dev/hidg0" + "\n");
			fixPermsOS.flush();
			fixPermsOS.writeBytes("magiskpolicy --live 'allow untrusted_app device chr_file { getattr open write }'" + "\n");
			fixPermsOS.flush();
			fixPermsOS.writeBytes("chcon u:object_r:device" + context + " /dev/hidg0" + "\n");
			fixPermsOS.flush();
			fixPermsOS.writeBytes("exit" + "\n");
			fixPermsOS.flush();
			//Timber.d("process output: stdout=%s,stderr=%s", getProcessStdOutput(fixPermsShell), getProcessStdError(fixPermsShell));
		} catch (IOException e) {
			Timber.e(Log.getStackTraceString(e));
			return false;
		}
		return true;
	}
}