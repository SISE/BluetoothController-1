package com.smjv.bluetooth.controller;

public class BlueTerm extends Activity {
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private static TextView mTitle;

    // Name of the connected device
    private String mConnectedDeviceName = null;

    /**
     * Set to true to add debugging code and logging.
     */
    public static final boolean DEBUG = true;

    /**
     * Set to true to log each character received from the remote process to the
     * android log, which makes it easier to debug some kinds of problems with
     * emulating escape sequences and control codes.
     */
    public static final boolean LOG_CHARACTERS_FLAG = DEBUG && false;

    /**
     * Set to true to log unknown escape sequences.
     */
    public static final boolean LOG_UNKNOWN_ESCAPE_SEQUENCES = DEBUG && false;

    /**
     * The tag we use when logging, so that our messages can be distinguished
     * from other messages in the log. Public because it's used by several
     * classes.
     */
	public static final String LOG_TAG = "BlueTerm";

    // Message types sent from the BluetoothReadService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;	

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
	
	private BluetoothAdapter mBluetoothAdapter = null;
	
    /**
     * Our main view. Displays the emulated terminal screen.
     */
//    private EmulatorView mEmulatorView;

    /**
     * A key listener that tracks the modifier keys and allows the full ASCII
     * character set to be entered.
     */
//    private TermKeyListener mKeyListener;
		
	
    private static BluetoothSerialService mSerialService = null;
    
	private static InputMethodManager mInputManager;
	
	private boolean mEnablingBT;
    private boolean mLocalEcho = false;
    private int mFontSize = 9;
    private int mColorId = 2;
    private int mControlKeyId = 0;

    private static final String LOCALECHO_KEY = "localecho";
    private static final String FONTSIZE_KEY = "fontsize";
    private static final String COLOR_KEY = "color";
    private static final String CONTROLKEY_KEY = "controlkey";

    public static final int WHITE = 0xffffffff;
    public static final int BLACK = 0xff000000;
    public static final int BLUE = 0xff344ebd;

    private static final int[][] COLOR_SCHEMES = {
        {BLACK, WHITE}, {WHITE, BLACK}, {WHITE, BLUE}};

    private static final int[] CONTROL_KEY_SCHEMES = {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_AT,
        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_ALT_RIGHT
    };
//    private static final String[] CONTROL_KEY_NAME = {
//        "Ball", "@", "Left-Alt", "Right-Alt"
//    };
    private static String[] CONTROL_KEY_NAME;

    private int mControlKeyCode;

    private SharedPreferences mPrefs;
	
    private MenuItem mMenuItemConnect;

    
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (DEBUG)
			Log.e(LOG_TAG, "+++ ON CREATE +++");

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        readPrefs();

        CONTROL_KEY_NAME = getResources().getStringArray(R.array.entries_controlkey_preference);

    	mInputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);		
		
        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);
        

        
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if (mBluetoothAdapter == null) {
            finishDialogNoBluetooth();
			return;
		}
		
        setContentView(R.layout.term_activity);



        mSerialService = new BluetoothSerialService(this, mHandlerBT/*, mEmulatorView*/);        

		if (DEBUG)
			Log.e(LOG_TAG, "+++ DONE IN ON CREATE +++");
	}

	@Override
	public void onStart() {
		super.onStart();
		if (DEBUG)
			Log.e(LOG_TAG, "++ ON START ++");
		
		mEnablingBT = false;
	}

	@Override
	public synchronized void onResume() {
		super.onResume();

		if (DEBUG) {
			Log.e(LOG_TAG, "+ ON RESUME +");
		}
		
		if (!mEnablingBT) { // If we are turning on the BT we cannot check if it's enable
		    if ( (mBluetoothAdapter != null)  && (!mBluetoothAdapter.isEnabled()) ) {
			
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.alert_dialog_turn_on_bt)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.alert_dialog_warning_title)
                    .setCancelable( false )
                    .setPositiveButton(R.string.alert_dialog_yes, new DialogInterface.OnClickListener() {
                    	public void onClick(DialogInterface dialog, int id) {
                    		mEnablingBT = true;
                    		Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    		startActivityForResult(enableIntent, REQUEST_ENABLE_BT);			
                    	}
                    })
                    .setNegativeButton(R.string.alert_dialog_no, new DialogInterface.OnClickListener() {
                    	public void onClick(DialogInterface dialog, int id) {
                    		finishDialogNoBluetooth();            	
                    	}
                    });
                AlertDialog alert = builder.create();
                alert.show();
		    }		
		
		    if (mSerialService != null) {
		    	// Only if the state is STATE_NONE, do we know that we haven't started already
		    	if (mSerialService.getState() == BluetoothSerialService.STATE_NONE) {
		    		// Start the Bluetooth chat services
		    		mSerialService.start();
		    	}
		    }

		    if (mBluetoothAdapter != null) {
		    	readPrefs();
//	
		    }
		}
	}

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

//        mEmulatorView.updateSize();
    }

	@Override
	public synchronized void onPause() {
		super.onPause();
		if (DEBUG)
			Log.e(LOG_TAG, "- ON PAUSE -");


	}

    @Override
    public void onStop() {
        super.onStop();
        if(DEBUG)
        	Log.e(LOG_TAG, "-- ON STOP --");
    }


	@Override
	public void onDestroy() {
		super.onDestroy();
		if (DEBUG)
			Log.e(LOG_TAG, "--- ON DESTROY ---");
		
        if (mSerialService != null)
        	mSerialService.stop();
        
	}

    private void readPrefs() {
        mLocalEcho = mPrefs.getBoolean(LOCALECHO_KEY, mLocalEcho);
        mFontSize = readIntPref(FONTSIZE_KEY, mFontSize, 20);
        mColorId = readIntPref(COLOR_KEY, mColorId, COLOR_SCHEMES.length - 1);
        mControlKeyId = readIntPref(CONTROLKEY_KEY, mControlKeyId,
                CONTROL_KEY_SCHEMES.length - 1);
    }



    private int readIntPref(String key, int defaultValue, int maxValue) {
        int val;
        try {
            val = Integer.parseInt(
                mPrefs.getString(key, Integer.toString(defaultValue)));
        } catch (NumberFormatException e) {
            val = defaultValue;
        }
        val = Math.max(0, Math.min(val, maxValue));
        return val;
    }
    
	public int getConnectionState() {
		return mSerialService.getState();
	}

//	public void s1(View view){
//		int ascii = (int) 'j';
//		on(ascii);//
//	}
//	
//	public void s2(View view){
//		int ascii = (int) 'k';
//		on(ascii);//
//	}
	
	public void onForward(View view) {
	    // Is the toggle on?
	    boolean on = ((ToggleButton) view).isChecked();
	    
	    if (on) {
	    	int ascii = 'w';
			on(ascii);//
	    } else {
	    	int ascii = 't';
			on(ascii);//
	    }
	}
	
	public void onBackward(View view) {
	    // Is the toggle on?
	    boolean on = ((ToggleButton) view).isChecked();
	    
	    if (on) {
	    	int ascii = 's';
			on(ascii);//
	    } else {
	    	int ascii = 'g';
			on(ascii);//
	    }
	}
	
	public void onLeft(View view) {
	    // Is the toggle on?
	    boolean on = ((ToggleButton) view).isChecked();
	    
	    if (on) {
	    	int ascii = 'a';
			on(ascii);//
	    } else {
	    	int ascii = 'f';
			on(ascii);//
	    }
	}
	
	public void onRight(View view) {
	    // Is the toggle on?
	    boolean on = ((ToggleButton) view).isChecked();
	    
	    if (on) {
	    	int ascii = 'd';
			on(ascii);// 
	    } else {
	    	int ascii = 'h';
			on(ascii);//
	    }
	}
	
	public void on(int c){
    	byte[] mBuffer = new byte[1];
    	mBuffer[0] = (byte)c;
    	send(mBuffer);
	}

    public void send(byte[] out) {
    	mSerialService.write( out );
    }
    
    public void toggleKeyboard() {
  		mInputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }
    
    public int getTitleHeight() {
    	return mTitle.getHeight();
    }
    
    // The Handler that gets information back from the BluetoothService
    private final Handler mHandlerBT = new Handler() {
    	
        @Override
        public void handleMessage(Message msg) {        	
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(DEBUG) Log.i(LOG_TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothSerialService.STATE_CONNECTED:
                	if (mMenuItemConnect != null) {
                		mMenuItemConnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
                		mMenuItemConnect.setTitle(R.string.disconnect);
                	}
                	
                	
                    mTitle.setText(R.string.title_connected_to);
                    mTitle.append(mConnectedDeviceName);
                    break;
                    
                case BluetoothSerialService.STATE_CONNECTING:
                    mTitle.setText(R.string.title_connecting);
                    break;
                    
                case BluetoothSerialService.STATE_LISTEN:
                case BluetoothSerialService.STATE_NONE:
                	if (mMenuItemConnect != null) {
                		mMenuItemConnect.setIcon(android.R.drawable.ic_menu_search);
                		mMenuItemConnect.setTitle(R.string.connect);
                	}

//            		mInputManager.hideSoftInputFromWindow(mEmulatorView.getWindowToken(), 0);
                	
                    mTitle.setText(R.string.title_not_connected);

                    break;
                }
                break;
            case MESSAGE_WRITE:
            	if (mLocalEcho) {
            		byte[] writeBuf = (byte[]) msg.obj;
            		Log.d("++++++", "mlocal"+ writeBuf.toString());
//            		mEmulatorView.write(writeBuf, msg.arg1);
            	}
                
                break;
/*                
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;              
                mEmulatorView.write(readBuf, msg.arg1);
                
                break;
*/                
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };    

    
    public void finishDialogNoBluetooth() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_dialog_no_bt)
        .setIcon(android.R.drawable.ic_dialog_info)
        .setTitle(R.string.app_name)
        .setCancelable( false )
        .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       finish();            	
                	   }
               });
        AlertDialog alert = builder.create();
        alert.show(); 
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(DEBUG) Log.d(LOG_TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        
        case REQUEST_CONNECT_DEVICE:

            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mSerialService.connect(device);                
            }
            break;

        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                Log.d(LOG_TAG, "BT not enabled");
                
                finishDialogNoBluetooth();                
            }
        }
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (handleControlKey(keyCode, true)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            // Don't intercept the system keys
        	Log.d("++++++", "systemkey");
            return super.onKeyDown(keyCode, event);
        } else if (handleDPad(keyCode, true)) {
            return true;
        }

        // Translate the keyCode into an ASCII character.
//        int letter = mKeyListener.keyDown(keyCode, event);
        int	letter = 107;
        if (letter >= 0) {
        	byte[] buffer = new byte[1];
        	buffer[0] = (byte)letter;
        	mSerialService.write(buffer);
        }
        return true;
    }

 
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (handleControlKey(keyCode, false)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            // Don't intercept the system keys
            return super.onKeyUp(keyCode, event);
        } else if (handleDPad(keyCode, false)) {
            return true;
        }

//        mKeyListener.keyUp(keyCode);
        return true;
    }

    private boolean handleControlKey(int keyCode, boolean down) {
        if (keyCode == mControlKeyCode) {
//            mKeyListener.handleControlKey(down);
            return true;
        }
        return false;
    }

    /**
     * Handle dpad left-right-up-down events. Don't handle
     * dpad-center, that's our control key.
     * @param keyCode
     * @param down
     */
    private boolean handleDPad(int keyCode, boolean down) {
    	byte[] buffer = new byte[1];

        if (keyCode < KeyEvent.KEYCODE_DPAD_UP ||
                keyCode > KeyEvent.KEYCODE_DPAD_CENTER) {
            return false;
        }

        if (down) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            	buffer[0] = '\r';
            	mSerialService.write( buffer );
            } else {
                char code;
                switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    code = 'A';
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    code = 'B';
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    code = 'D';
                    break;
                default:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    code = 'C';
                    break;
                }
            	buffer[0] = 27; // ESC
            	mSerialService.write( buffer );                    
//                if (mEmulatorView.getKeypadApplicationMode()) {
//                	buffer[0] = 'O';
//                	mSerialService.write( buffer );                    
//                } else {
//                	buffer[0] = '[';
//                	mSerialService.write( buffer );                    
//                }
            	buffer[0] = (byte)code;
            	mSerialService.write( buffer );                    
            }
        }
        return true;
    }

    private boolean isSystemKey(int keyCode, KeyEvent event) {
        return event.isSystem();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        mMenuItemConnect = menu.getItem(0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.connect:
        	
        	if (getConnectionState() == BluetoothSerialService.STATE_NONE) {
        		// Launch the DeviceListActivity to see devices and do scan
        		Intent serverIntent = new Intent(this, DeviceListActivity.class);
        		startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
        	}
        	else
            	if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED) {
            		mSerialService.stop();
		    		mSerialService.start();
            	}
            return true;
        case R.id.preferences:
        	doPreferences();
            return true;
        case R.id.menu_special_keys:
            doDocumentKeys();
            return true;
        }
        return false;
    }
    
    private void doPreferences() {
        startActivity(new Intent(this, TermPreferences.class));
    }



    private void doDocumentKeys() {
        String controlKey = CONTROL_KEY_NAME[mControlKeyId];
        new AlertDialog.Builder(this).
            setTitle( getString(R.string.title_document_key_press) + " \"" + controlKey + "\" "+ getString(R.string.title_document_key_rest)).
            setMessage(" Space ==> Control-@ (NUL)\n"
                    + " A..Z ==> Control-A..Z\n"
                    + " I ==> Control-I (TAB)\n"
                    + " 1 ==> Control-[ (ESC)\n"
                    + " 5 ==> Control-_\n"
                    + " . ==> Control-\\\n"
                    + " 0 ==> Control-]\n"
                    + " 6 ==> Control-^").
            show();
     }
}



/**
 * A multi-thread-safe produce-consumer byte array.
 * Only allows one producer and one consumer.
 */

class ByteQueue {
    public ByteQueue(int size) {
        mBuffer = new byte[size];
    }

    public int getBytesAvailable() {
        synchronized(this) {
            return mStoredBytes;
        }
    }

    public int read(byte[] buffer, int offset, int length)
        throws InterruptedException {
        if (length + offset > buffer.length) {
            throw
                new IllegalArgumentException("length + offset > buffer.length");
        }
        if (length < 0) {
            throw
            new IllegalArgumentException("length < 0");

        }
        if (length == 0) {
            return 0;
        }
        synchronized(this) {
            while (mStoredBytes == 0) {
                wait();
            }
            int totalRead = 0;
            int bufferLength = mBuffer.length;
            boolean wasFull = bufferLength == mStoredBytes;
            while (length > 0 && mStoredBytes > 0) {
                int oneRun = Math.min(bufferLength - mHead, mStoredBytes);
                int bytesToCopy = Math.min(length, oneRun);
                System.arraycopy(mBuffer, mHead, buffer, offset, bytesToCopy);
                mHead += bytesToCopy;
                if (mHead >= bufferLength) {
                    mHead = 0;
                }
                mStoredBytes -= bytesToCopy;
                length -= bytesToCopy;
                offset += bytesToCopy;
                totalRead += bytesToCopy;
            }
            if (wasFull) {
                notify();
            }
            return totalRead;
        }
    }

    public void write(byte[] buffer, int offset, int length)
    throws InterruptedException {
        if (length + offset > buffer.length) {
            throw
                new IllegalArgumentException("length + offset > buffer.length");
        }
        if (length < 0) {
            throw
            new IllegalArgumentException("length < 0");

        }
        if (length == 0) {
            return;
        }
        synchronized(this) {
            int bufferLength = mBuffer.length;
            boolean wasEmpty = mStoredBytes == 0;
            while (length > 0) {
                while(bufferLength == mStoredBytes) {
                    wait();
                }
                int tail = mHead + mStoredBytes;
                int oneRun;
                if (tail >= bufferLength) {
                    tail = tail - bufferLength;
                    oneRun = mHead - tail;
                } else {
                    oneRun = bufferLength - tail;
                }
                int bytesToCopy = Math.min(oneRun, length);
                System.arraycopy(buffer, offset, mBuffer, tail, bytesToCopy);
                offset += bytesToCopy;
                mStoredBytes += bytesToCopy;
                length -= bytesToCopy;
            }
            if (wasEmpty) {
                notify();
            }
        }
    }

    private byte[] mBuffer;
    private int mHead;
    private int mStoredBytes;
}