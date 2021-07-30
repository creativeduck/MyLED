package com.mybest.myled;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.imageview.ShapeableImageView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import petrov.kristiyan.colorpicker.ColorPicker;
import top.defaults.colorpicker.ColorPickerPopup;


public class MainActivity extends AppCompatActivity {
    ImageView colorPick; // 색상 선택하는 이미지
    ShapeableImageView selectedColor; // 선택된 색상 보여주는 이미지
    TextView currentState; // 현재 색상과 밝기 표시할 텍스트뷰
    SeekBar bar; // 밝기 조절할 시크바
    ImageButton btnRainbow; // 레인보우 버튼
    ImageButton btnPower; // 전원 버튼
    ImageButton btnColorPicker; // 컬러피커 버튼
    ImageButton btnConnect; // 블루투스 재접속 버튼

    Button btnPut;

    byte[] colors = new byte[5]; // 아두이노로 데이터를 보낼 배열
    boolean powerOn = false; // 전원이 켜졌는지 여부

    static final int REQUEST_ENABLE_BT = 100; // 블루투스 요청 코드값 정의
    static final int REQUEST_PERMISSIONS = 101; // 위험 권한 요청 코드값
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); // 블루투스 어댑터
    BluetoothDevice bluetoothDevice;    // 블루투스 장치
    Set<BluetoothDevice> pairedDevices; // 페어링된 장치 집합
    Set<BluetoothDevice> unpairedDevices = new HashSet<>(); // 페어링되지 않은 장치 집합
    ArrayAdapter<String> adapter; // 기기 검색에 필요한 어댑터
    List<String> unpairedList = new ArrayList<>(); // 페어링되지 않은 장치이름 목록
    BluetoothSocket bluetoothSocket;
    OutputStream outputStream;
    InputStream inputStream;

    boolean paired=false;

    Thread receiveThread;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() { // 브로드캐스트 리시버
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {   // 전달받은 액션이 ACTION_FOUND 라면
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    if(device.getName() != null) {
                        add(device);
                    }
                }
            }
        }
    };
    private void add(BluetoothDevice device) {
        if(!(pairedDevices.contains(device))) { // 페어링된 장치에 없고
            if(unpairedDevices.add(device)) { // 장치가 추가된다면(중복이 아니라면)
                unpairedList.add(device.getName());    // 페어링되지 않은 장치이름 목록에 이름 추가
            }
        }
        adapter.notifyDataSetChanged(); // 변화 반영
        Toast.makeText(this, device.getName()+" 검색", Toast.LENGTH_SHORT).show();
    }

    // 장치가 블루투스 지원하는지 확인
    private void checkBluetooth() {
        if(bluetoothAdapter==null) {
            Toast.makeText(getApplicationContext(), "해당 기기는 블루투스를 지원하지 않습니다.",
                                                        Toast.LENGTH_SHORT).show();
            finish();
        } else {
            if(bluetoothAdapter.isEnabled()) {
                selectPairedDevice();
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }
    // 블루투스 활성화 여부에 따른 결과
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode==REQUEST_ENABLE_BT) {
            if(resultCode==RESULT_OK) {
                selectPairedDevice();
            } else if(resultCode==RESULT_CANCELED) {
                finish();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    // 기기검색
    // 이미 페어링된 블루투스 기기 검색
    private void selectPairedDevice() {
        pairedDevices = bluetoothAdapter.getBondedDevices();

        AlertDialog.Builder builder = new AlertDialog.Builder(this); // 다이얼로그 생성
        builder.setTitle("기기 선택");

        List<String> pairedList = new ArrayList<>(); // 페어링된 기기이름 목록
        for(BluetoothDevice device : pairedDevices) { // 페어링된 기기 집합에서
            pairedList.add(device.getName());        // 장치 이름 전부 기기목록에 추가
        }
        pairedList.add("취소"); // 취소버튼 추가

        final CharSequence[] devices = pairedList.toArray(new CharSequence[pairedList.size()]);
        builder.setItems(devices, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == pairedList.size()-1) { // 맨 마지막 버튼이 취소 버튼
                    selectUnpairedDevice(); // 페어링되지 않은 기기 검색
                } else {
                    bluetoothAdapter.cancelDiscovery();
                    paired = true;
                    connectDevice(devices[which].toString(), paired); // 선택된 인덱스에 해당하는 기기 연결
                }
            }
        });
        builder.setCancelable(false);    // 배경을 선택하면 무력화되는 것을 막기 위함
        AlertDialog dialog = builder.create(); // 빌더로 다이얼로그 만들기
        dialog.show();   // 다이얼로그 시작
    }

    // 페어링되지 않은 기기 검색
    private void selectUnpairedDevice() {
        // 이미 검색 중이라면 검색을 종료하고, 다시 검색 시작
        if(bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();

        AlertDialog.Builder builder = new AlertDialog.Builder(this); // 다이얼로그 생성
        builder.setTitle("기기 검색");

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, unpairedList);

        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                bluetoothAdapter.cancelDiscovery();
                String name = adapter.getItem(which);
                unpairedList.remove(name);
                paired = false;
                connectDevice(name, paired);
            }
        });
        AlertDialog dialog = builder.create(); // 빌더로 다이얼로그 만들기
        dialog.show();
    }

    // 기기 연결 준비
    // 블루투스 페어링된 목록에서 디바이스 장치 가져오기
    private BluetoothDevice getPairedDevice(String name) {
        BluetoothDevice selectedDevice = null;

        for(BluetoothDevice device : pairedDevices) {
            if(name.equals(device.getName())) {
                selectedDevice = device;
                break;
            }
        }
        return selectedDevice;
    }
    // 페어링되지 않은 장치 목록에서 디바이스 장치 가져오기
    private BluetoothDevice getUnpairedDevice(String name) {
        BluetoothDevice selectedDevice = null;

        for(BluetoothDevice device : unpairedDevices) {
            if(name.equals(device.getName())) {
                selectedDevice = device;
                break;
            }
        }
        return selectedDevice;
    }

    // 기기 연결
    private void connectDevice(String selectedDeviceName, boolean paired) {
        final Handler mHandler = new Handler() {
            public void handleMessage(Message msg) {
                if(msg.what==1) {
                    try{
                        // 소켓으로 데이터 송수신을 위한 스트림 객체 얻는다.
                        outputStream = bluetoothSocket.getOutputStream();
                        inputStream = bluetoothSocket.getInputStream();
                        // receiveDate();
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    Toast.makeText(getApplicationContext(), "연결 오류", Toast.LENGTH_SHORT).show();
                    try {
                        bluetoothSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        Thread thread = new Thread(new Runnable() {
            public void run() {
                if(paired)
                    bluetoothDevice = getPairedDevice(selectedDeviceName); // 페어링된 목록에서 선택한 이름의 블루투스 객체를 받는다.
                else
                    bluetoothDevice = getUnpairedDevice(selectedDeviceName);
                // 블루투스 장치와 통신하기 위해선 소켓 생성시 UUID가 필요하다.
                // 중복되지 않는 고유 식별키를 생성해서 우리가 필요로 하는 uuid를 얻는다.
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // 블루투스 통신에선 다음 값을 사용한다.
                // UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); // 블루투스 통신에선 다음 값을 사용한다.

                try {
                    // 블루투스 통신은 소켓을 생성해서 한다. 소켓을 얻기 위해선 createRfcommSocketToServiceRecord() 메소드를 사용한다.
                    // 앞서 얻은 블루투스 객체로 이 메소드를 사용해 블루투스 모듈과 통신할 수 있는 소켓을 생성한다. 매개변수로 uuid를 넣어주어야 한다.
                    bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
                    // RFCOMM 채널을 통한 연결, socket에 connect하는데 시간이 걸린다. 따라서 ui에 영향을 주지 않기 위해서는
                    // Thread로 연결 과정을 수행해야 한다.
                    bluetoothSocket.connect();
                    mHandler.sendEmptyMessage(1);
                } catch (IOException e) {
                    // 블루투스 연결 중 오류 발생
                    e.printStackTrace();
                    mHandler.sendEmptyMessage(-1);
                }
            }
        });
        //연결 thread를 수행한다
        thread.start();
    }

    private void receiveDate() {
        final Handler handler = new Handler();

        // 문자열 수신 쓰레드
        receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        int bytesAvailable = inputStream.available();
                        if (bytesAvailable > 0) { //데이터가 수신된 경우
                            byte[] readColor = new byte[4];
                            SystemClock.sleep(50);
                            inputStream.read(readColor);

                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    currentState.setText("R: "+ (readColor[0] & 0xFF)
                                            +" G: "+ (readColor[1] & 0xFF)
                                            +" B: "+ (readColor[2] & 0xFF)
                                            +" 밝기: "+(readColor[3] & 0xFF));
                                }
                            });
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        //데이터 수신 thread 시작
        receiveThread.start();
    }

    // 액티비티가 종료되기 직전 호출되는 함수
    @Override
    protected void onDestroy() {
        try{
            inputStream.close(); // 입력 스트림 닫아주기
            outputStream.close(); // 출력 스트림 닫아주기
            bluetoothSocket.close(); // 소켓 닫아주기
            receiveThread.interrupt();


        } catch(IOException e) {
            e.printStackTrace();
        }
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
        checkPermissions(permissions);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        btnPower = findViewById(R.id.btnPower);
        selectedColor =  findViewById(R.id.selectedColor);
        colorPick = findViewById(R.id.colorPick);
        btnRainbow = findViewById(R.id.btnRainbow);
        currentState = findViewById(R.id.currentState);
        bar = findViewById(R.id.bar);
        btnConnect = findViewById(R.id.btnReconnect);
        btnColorPicker = findViewById(R.id.btnColorPicker);


        // 색상 선택하면 색상 데이터 전송해서 LED 불빛 제어
       colorPick.setOnTouchListener(new View.OnTouchListener() { // 터치 리스너 상속
            public boolean onTouch(View v, MotionEvent event) { // 색상이 터치되면
                // 터치된 곳의 좌표 가져오기
                final int evX = (int) event.getX();
                final int evY = (int) event.getY();

                Bitmap mBitmap = getBitmapFromView(colorPick);

                try {
                    int pixel = mBitmap.getPixel(evX, evY); // 비트맵 객체로부터 터치된 곳의 픽셀 가져오기
                    sendData(pixel); // 해당 픽셀의 색상 데이터를 전송해서 LED 나오게 하기
                    powerOn=true; // 전원 켜짐
                }catch (IllegalStateException e){
                    e.printStackTrace();
                } catch(IllegalArgumentException e) {
                    e.printStackTrace();
                }
                mBitmap.recycle(); // 더 이상 참조하는 곳이 없다면 버려지도록 가비지 콜렉터로 보냄
                return true;
            }
        });

        // 초기 색상은 하얀색으로 설정(전원 버튼을 위한 설정)
        colors[0] = (byte) 255;
        colors[1] = (byte) 255;
        colors[2] = (byte) 255;
        // 전원 버튼
        btnPower.setOnClickListener(e-> {
            power();
        });
        // 블루투스 접속 버튼
        btnConnect.setOnClickListener(e-> {
            checkBluetooth();
        });
        // 레인보우 버튼
        btnRainbow.setOnClickListener(e-> {
            rainbow();
            selectedColor.setImageResource(R.drawable.rainbow); // 선택된 색상 표시 이미지에 레인보우 이미지 나타내기
        });
        // 컬러피커
        btnColorPicker.setOnClickListener(e-> {
            colorPicker();
        });
        // 밝기 조절
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                colors[3] = (byte) seekBar.getProgress(); // 현재 시크바 값을 밝기 데이터로 설정하기
                sendData();                                  // 데이터 전송
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
    // 뷰로부터 비트맵 객체 만들기
    public Bitmap getBitmapFromView(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }
    private void setCurrentState() {
        currentState.setText("R: " + (colors[0] & 0xFF) + " G: " + (colors[1] & 0xFF) +
                             " B: " + (colors[2] & 0xFF) + " 밝기: "+ (colors[3] & 0xFF));
    }
    // 색상 및 밝기 데이터 블루투스로 보내기
    private void sendData() {
        try {
            outputStream.write(colors); // 출력 스트림으로 데이터 배열 전송
        } catch(Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "데이터를 전송할 수 없습니다", Toast.LENGTH_SHORT).show();
        }
        setCurrentState();
    }
    // color나 pixel을 파라미터로 전달받았을 때 동작
    private void sendData(int cOrp) {
        colors[0] = (byte) Color.red(cOrp); // 빨간색 설정
        colors[1] = (byte) Color.green(cOrp); // 초록색 설정
        colors[2] = (byte) Color.blue(cOrp); // 파란색 설정
        colors[3] = (byte) bar.getProgress(); // 밝기 설정
        selectedColor.setImageDrawable(null); // 선택된 색상을 보여주는 이미지뷰에 설정되어있는 이미지 지우기
        selectedColor.setBackgroundColor(Color.rgb(Color.red(cOrp), Color.green(cOrp), 
                                                    Color.blue(cOrp))); // 현재 선택된 색상으로 배경색 설정
        try {
            outputStream.write(colors); // 출력 스트림으로 데이터 배열 전송
        } catch(Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "데이터를 전송할 수 없습니다", Toast.LENGTH_SHORT).show();
        }
        setCurrentState();
    }
    // 레인보우 함수
    private void rainbow() {
        colors[4] = 1; // 레인보우 O
        sendData();
        currentState.setText("rainbow");
        powerOn=true; // 전원 켜짐
        colors[4] = 0;
    }
    // 전원 함수
    private void power() {
        if(powerOn) {   // 전원이 켜져있다면
            bar.setProgress(0); // 시크바 값 0으로 설정
            sendData();                              // 밝기 데이터 전송
            powerOn=false;                          // 전원 꺼짐
        }
        else { // 전원이 꺼져있다면
            bar.setProgress(255); // 시크바 값 255로 설정
            sendData();            // 밝기 데이터 전송
            powerOn=true;         // 전원 켜짐
        }
    }

    // 컬러피커 1
    private void colorPicker() {
        final ColorPicker colorPicker = new ColorPicker(this);  // ColorPicker 객체 생성
        ArrayList<String> colors = new ArrayList<>();  // Color 넣어줄 list

        colors.add("#e8472e");
        colors.add("#ff8c8c");
        colors.add("#f77f23");
        colors.add("#e38436");
        colors.add("#f0e090");
        colors.add("#98b84d");
        colors.add("#a3d17b");
        colors.add("#6ecf69");
        colors.add("#6ec48c");
        colors.add("#5aa392");
        colors.add("#4b8bbf");
        colors.add("#6a88d4");
        colors.add("#736dc9");
        colors.add("#864fbd");
        colors.add("#d169ca");
        colorPicker.setColors(colors);

        colorPicker.setColumns(5)  // 5열로 설정
                .setRoundColorButton(true)  // 원형 버튼으로 설정
                .setTitle("색상 선택")
                .setOnChooseColorListener(new ColorPicker.OnChooseColorListener() {
                    @Override
                    public void onChooseColor(int position, int color) {
                        sendData(color);
                        powerOn=true;
                    }
                    @Override
                    public void onCancel() {}
                }).show();  // dialog 생성
    }
    // 컬러피커 2
    private void colorPicker2() {
        new ColorPickerPopup.Builder(this)
                .initialColor(Color.WHITE)
                .enableAlpha(false)
                .enableBrightness(true) // 밝기와 투명도는 여기서 선택해도 의미가 없으르모 배제
                .okTitle("선택")
                .cancelTitle("취소")
                .showIndicator(true) // 어떤 색 가리키고 있는지 확인
                .showValue(false) // 어떤 값 가리키는지 확인(but 필요없음)
                .build()
                .show(new ColorPickerPopup.ColorPickerObserver() {
                    @Override
                    public void onColorPicked(int color) {
                        sendData(color);
                    }
                });
    }

    private void checkPermissions(String[] permissions) {
        ArrayList<String> requestList = new ArrayList<>();

        for(int i=0; i<permissions.length; i++) {
            String curPermission = permissions[i];
            int permissionCheck = ContextCompat.checkSelfPermission(this, curPermission);
            if(permissionCheck == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), curPermission+" 권한 있음", Toast.LENGTH_SHORT).show();
            } else {
                if(ActivityCompat.shouldShowRequestPermissionRationale(this,curPermission)) {
                    Toast.makeText(getApplicationContext(), curPermission+" 권한 설명 필요", Toast.LENGTH_SHORT).show();
                } else {
                    requestList.add(curPermission);
                }

            }
        }
        if(requestList.size()>0) {
            String[] requests = requestList.toArray(new String[requestList.size()]);
            ActivityCompat.requestPermissions(this, requests, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0) {
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, (i+1)+"번째 권한 승인", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, (i+1)+"번째 권한 거부", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}