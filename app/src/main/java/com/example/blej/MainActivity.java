package com.example.blej;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
import static android.content.ContentValues.TAG;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

public class MainActivity extends AppCompatActivity {
    BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
    ScanSettings scanSettings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setReportDelay(0L)
            .build();
    BluetoothLeScanner scanner = btAdapter.getBluetoothLeScanner();
    Button startB, connectB,DisconnectB;
    TextView Device, Status, Services, charac;
    ListView list;
    BluetoothDevice device;
    BluetoothGatt blGatt;
    Handler bleHandler;
    private Queue<Runnable> commandQueue;
    private boolean commandQueueBusy,isRetrying;
    int nrTries=0;
    ArrayList<BluetoothDevice> devicesDiscovered = new ArrayList<BluetoothDevice>();

    private static final int ACCESS_LOCATION_REQUEST = 2;
    private static final int MAX_TRIES = 10;
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    String[] peripheralAddresses = new String[]{"00:80:E1:26:69:DD"};
    List<ScanFilter> filters = null;


    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        startB = findViewById(R.id.buttonStart);
        connectB = findViewById(R.id.buttonConnect);
        DisconnectB = findViewById(R.id.buttonDisconnect);
        Device = findViewById(R.id.Device);
        Status = findViewById(R.id.Status);
        Services = findViewById(R.id.Services);
        charac = findViewById(R.id.charac);
        //list = findViewById(R.id.list);
        Services.setMovementMethod(new ScrollingMovementMethod());
        Device.setMovementMethod(new ScrollingMovementMethod());

        if (peripheralAddresses != null) {
            filters = new ArrayList<>();
            for (String address : peripheralAddresses) {
                ScanFilter filter = new ScanFilter.Builder()
                        .setDeviceAddress(address)
                        .build();
                filters.add(filter);
            }
        }

        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @SuppressLint("NewApi")
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();
        }
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }

    @SuppressLint("MissingPermission")
    public void onClick_Start(View v) {

        if (scanner != null) {
            scanner.startScan(filters, scanSettings, scanCallback);
            Log.d(TAG, "scan started");
        } else {
            Log.e(TAG, "could not get scanner object");
        }

    }
    public void buttonDisconnect(View v){

        Status.setText("Disconnected from device");
        Device.setText("No Device");
        blGatt.disconnect();



    }

    public void buttonConnect(View v) {

        blGatt = device.connectGatt(this, false, bluetoothGattCallback,TRANSPORT_LE);

    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
                device = result.getDevice();
                Device.setText(device.getName());
            // ...do whatever you want with this found device
        }


    };

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status,
                                            final int newState) {
            if (status == GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    int bondstate = device.getBondState();
                    // Take action depending on the bond state
                    if (bondstate == BOND_NONE || bondstate == BOND_BONDED) {
                        Status.setText("Device connected");
                        // Connected to device, now proceed to discover it's services but delay a bit if needed
                        int delayWhenBonded = 0;
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                            delayWhenBonded = 1000;
                        }
                        final int delay = bondstate == BOND_BONDED ? delayWhenBonded : 0;
                                Log.d(TAG, String.format(Locale.ENGLISH, "discovering services of '%s' with delay of %d ms", btAdapter.getName(), delay));
                                boolean result = gatt.discoverServices();
                                    if (!result)
                                    Log.e(TAG, "discoverServices failed to start");
                    } else if (bondstate == BOND_BONDING) {
                        // Bonding process in progress, let it complete
                        Log.i(TAG, "waiting for bonding to complete");
                    }

                }
            }

        }
        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status){
            if (status == 129) Log.e(TAG, "Service discovery failed");
            else Services.setText("Service discovery succeeded!");
            if (gatt.getServices() == null) Services.setText("No services discovered!");
            byte[] b = new byte[] { (byte)0x07, (byte) 0xf1};

            String charneeded = "0000fe41-8e22-4541-9d4c-21edae82ed19";


            for (BluetoothGattService gattService : blGatt.getServices()) {

                final String uuid = gattService.getUuid().toString();
                System.out.println("Service discovered: " + uuid);
                Services.setText(uuid+"\n");
                new ArrayList<HashMap<String, String>>();
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();

                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic :
                        gattCharacteristics) {

                    final String charUuid = gattCharacteristic.getUuid().toString();



                    System.out.println("Characteristic discovered for service: " + charUuid);
                    charac.setText(charUuid+"\n");
                    if((gattCharacteristic.getProperties() & PROPERTY_WRITE_NO_RESPONSE) == 0) {
                        System.out.println("CHAR CAN BE WRITTEN TO \n");

                        gattCharacteristic.setValue(b);
                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        blGatt.writeCharacteristic(gattCharacteristic);
                    }
                }
            }




        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
            // Perform some checks on the status field
            if (status != GATT_SUCCESS) {
                Log.e(TAG, String.format(Locale.ENGLISH,"ERROR: Read failed for characteristic: %s, status %d", characteristic.getUuid(), status));
                //completedCommand();
                return;
            }

            // Characteristic has been read so processes it
            System.out.println("Characteristic value " + characteristic.getValue() +"\n");

            // We done, complete the command
            //completedCommand();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // this will get called anytime you perform a read or write characteristic operation
            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    Status.append("device read or wrote to\n");
                }
            });
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "Characteristic " + characteristic.getUuid() + " written");
        }
    };


    public void buttonDiscover(View v){


        blGatt.discoverServices();



    }


}