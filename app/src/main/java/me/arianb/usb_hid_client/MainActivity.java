package me.arianb.usb_hid_client;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;


// TODO: make it detect modifier key combos (ctrl-c, ctrl-f, etc.)
//      ~` are broken
// some keys after +,',( sends twice. i have no idea why.
public class MainActivity extends AppCompatActivity {
	private EditText input;
	private Button btn;

	private String appFileDirectory;
	private String hidGadgetPath;

	private Map<Integer, String> keyEventCodes;
	private Map<Character, String> shiftChars;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		input = findViewById(R.id.etKeyboardInput);
		btn = findViewById(R.id.btnKeyboard);

		keyEventCodes = new HashMap<Integer, String>();
		shiftChars = new HashMap<Character, String>();

        keyEventCodes.put(131, "f1");
        keyEventCodes.put(132, "f2");
        keyEventCodes.put(133, "f3");
        keyEventCodes.put(134, "f4");
        keyEventCodes.put(135, "f5");
        keyEventCodes.put(136, "f6");
        keyEventCodes.put(137, "f7");
        keyEventCodes.put(138, "f8");
        keyEventCodes.put(139, "f9");
        keyEventCodes.put(140, "f10");
        keyEventCodes.put(141, "f11");
        keyEventCodes.put(142, "f12");
        keyEventCodes.put(61, "tab");
        keyEventCodes.put(67, "backspace");
        keyEventCodes.put(111, "escape");
        keyEventCodes.put(120, "print"); // and SysRq
        keyEventCodes.put(116, "scroll-lock");
        keyEventCodes.put(143, "num-lock");
        keyEventCodes.put(121, "pause");
        keyEventCodes.put(124, "insert");
        keyEventCodes.put(112, "delete");

        // Chars that are represented by another key + shift
		shiftChars.put('<', ",");
		shiftChars.put('>', ".");
		shiftChars.put('?', "/");
		shiftChars.put(':', ";");
		shiftChars.put('"', "'");
		shiftChars.put('{', "[");
		shiftChars.put('}', "]");
		shiftChars.put('|', "\\");
		shiftChars.put('~', "`");
		shiftChars.put('!', "1");
		shiftChars.put('@', "2");
		shiftChars.put('#', "3");
		shiftChars.put('$', "4");
		shiftChars.put('%', "5");
		shiftChars.put('^', "6");
		shiftChars.put('&', "7");
		shiftChars.put('*', "8");
		shiftChars.put('(', "9");
		shiftChars.put(')', "0");
		shiftChars.put('_', "-");
		shiftChars.put('+', "=");

		appFileDirectory = "/data/data/me.arianb.usb_hid_client";
		hidGadgetPath = appFileDirectory + "/hid-gadget";

		// Copy over binary
        copyAssets("hid-gadget");

        input.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				System.out.println("Diff: " + s); // DEBUG
				System.out.println(start + " " + before + " " + count); // DEBUG
				if (before > count) { // I think this conditional is accurate but haven't finished testing
					System.out.println("Backspace"); // DEBUG
					sendKey("backspace", false);
				} else {
					char newChar = s.subSequence(s.length() - 1, s.length()).charAt(0);
					String str = null;
					if ((str = shiftChars.get(newChar)) != null) {
						sendKey(str, true);
						System.out.println("elif: " + str); // DEBUG
					} else {
						sendKey(Character.toString(newChar), false);
						System.out.println("else: " + newChar); // DEBUG
					}
				}
			}
		});
    }


	private void sendKey(String str, Boolean pressShift) {
		String options = "";
		String key = str;
		if (pressShift) {
			options = "--left-shift";
		}

		// Switch case is probably cleaner for this, i should fix it later
		// Translate character
		if (str.equals("\n")) {
			key = "enter";
		}
		// Escape characters
		else if (str.equals("\"")) {
			key = "\\\"";
		} else if (str.equals("\\")) {
		    key = "\\\\";
        } else if (str.equals("`")) {
            key = "\\`";
        }

		if (str.length() == 1 && Character.isUpperCase(str.charAt(0))) {
			options = "--left-shift";
			key = str.toLowerCase();
		}
		String[] shell = {"su", "-c", "echo \"" + key + "\" " + options + " | " + hidGadgetPath + " /dev/hidg0 keyboard"};
		try {
			Process process = Runtime.getRuntime().exec(shell);
			//System.out.println(printProcessStdOutput(process));
			System.out.println(printProcessStdError(process));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// detects non-printing keys
	// TODO: handle issues of edittext watcher and onKeyDown listener detecting the same press
	//       currently not an issue, but it might become one later
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
        String str = null;
	    if ((str = keyEventCodes.get(event.getKeyCode())) != null) {
            sendKey(str, true);
            System.out.println("if: " + str); // DEBUG
        }
        System.out.println("CODE: " + event.getKeyCode());
		return false;
	}

	private void copyAssets(String filename) {
		AssetManager assetManager = getAssets();

		InputStream in = null;
		OutputStream out = null;
		Log.d("tag", "Attempting to copy this file: " + filename); // + " to: " +       assetCopyDestination);

		try {
			in = assetManager.open(filename);
			Log.d("tag", "outDir: " + appFileDirectory);
			File outFile = new File(appFileDirectory, filename);
			out = new FileOutputStream(outFile);
			byte[] buffer = new byte[102400];
			int len = in.read(buffer);
			while (len != -1) {
				out.write(buffer, 0, len);
				len = in.read(buffer);
			}
			in.close();
			in = null;
			out.flush();
			out.close();
			out = null;
			File execFile = new File(hidGadgetPath);
			execFile.setExecutable(true);
		} catch (IOException e) {
			Log.e("tag", "Failed to copy asset file: " + filename, e);
		}
		Log.d("tag", "Copy success: " + filename);
	}

	private String printProcessStdOutput(Process process) throws IOException {
		BufferedReader stdInput = new BufferedReader(new
				InputStreamReader(process.getInputStream()));
		// Read the output from the command
		System.out.println("Here is the standard output of the command:\n");
		String s = null;
		StringBuilder returnStr = new StringBuilder();
		while ((s = stdInput.readLine()) != null) {
			returnStr.append(s).append("\n");
		}
		return returnStr.toString();
	}

	private String printProcessStdError(Process process) throws IOException {
		BufferedReader stdError = new BufferedReader(new
				InputStreamReader(process.getErrorStream()));
		// Read any errors from the attempted command
		System.out.println("Here is the standard error of the command (if any):\n");
		String s = null;
		StringBuilder returnStr = new StringBuilder();
		while ((s = stdError.readLine()) != null) {
			returnStr.append(s).append("\n");
		}
		return returnStr.toString();
	}
}