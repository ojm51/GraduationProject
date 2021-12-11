package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener, OnMapReadyCallback {

    private SensorManager mSensorManger;
    private Sensor linearSensor;
    private Sensor gyroSensor;
    private Queue axque;
    private Queue ayque;
    private Queue azque;
    private Queue gxque;
    private Queue gyque;
    private Queue gzque;
    private KalmanFilter mKalmanAccX;
    private KalmanFilter mKalmanAccY;
    private KalmanFilter mKalmanAccZ;
    private KalmanFilter mKalmanGyroX;
    private KalmanFilter mKalmanGyroY;
    private KalmanFilter mKalmanGyroZ;

    private int num = 0;

    float ax = (float) 0.0;
    float ay = (float) 0.0;
    float az = (float) 0.0;
    float gx = (float) 0.0;
    float gy = (float) 0.0;
    float gz = (float) 0.0;
    float Kalax = (float) 0.0;
    float Kalay = (float) 0.0;
    float Kalaz = (float) 0.0;
    float Kalgx = (float) 0.0;
    float Kalgy = (float) 0.0;
    float Kalgz = (float) 0.0;
    float axmax = (float) 0.0;
    float axmin = (float) 0.0;
    float aymax = (float) 0.0;
    float aymin = (float) 0.0;
    float azmax = (float) 0.0;
    float azmin = (float) 0.0;
    float gxmax = (float) 0.0;
    float gxmin = (float) 0.0;
    float gymax = (float) 0.0;
    float gymin = (float) 0.0;
    float gzmax = (float) 0.0;
    float gzmin = (float) 0.0;
    float CVA = (float) 0.0;

    int hasSMSPermission;
    int hasFineLocationPermission;
    int hasCoarseLocationPermission;

    private static final int GPS_ENABLE_REQUEST_CODE = 2001;
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    String[] REQUIRED_PERMISSIONS  = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.SEND_SMS};

    private GpsTracker gpsTracker;
    private FusedLocationProviderClient fusedLocationClient;    // 마지막으로 알려진 사용자 위치 가져오기

    private Dialog dialog;
    private Handler dHandler;

    BottomNavigationView bottomNavigation;
    private TextView textView1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 퍼미션을 가지고 있는지 체크함
        hasSMSPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS);
        hasFineLocationPermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION);
        hasCoarseLocationPermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION);

        mKalmanAccX=new KalmanFilter(0.0f);
        mKalmanAccY=new KalmanFilter(0.0f);
        mKalmanAccZ=new KalmanFilter(0.0f);
        mKalmanGyroX=new KalmanFilter(0.0f);
        mKalmanGyroY=new KalmanFilter(0.0f);
        mKalmanGyroZ=new KalmanFilter(0.0f);

        axque=new Queue();
        ayque=new Queue();
        azque=new Queue();
        gxque=new Queue();
        gyque=new Queue();
        gzque=new Queue();


        mSensorManger=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
        linearSensor = mSensorManger.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyroSensor=mSensorManger.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        // 주소값 읽어오기
        if (!checkLocationServicesStatus()) {
            showDialogForLocationServiceSetting();
        } else {
            checkRunTimePermission();
        }

        // 메시지 전송 확인창
        dialog=new Dialog(MainActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog);
        dHandler=new Handler();

        // 네비게이션바(메뉴바)
        bottomNavigation=findViewById(R.id.nav_view);
        bottomNavigation.setOnItemSelectedListener(new TabSelected());
        textView1=(TextView)findViewById(R.id.text1);

        // 구글지도 불러오기
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            // Logic to handle location object
                        }
                    }
                });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

    }

    class TabSelected implements NavigationBarView.OnItemSelectedListener {
        @SuppressLint("NonConstantResourceId")
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch ((item.getItemId())){
                case R.id.navigation_detection:{
                    gpsTracker = new GpsTracker(MainActivity.this);
                    double latitude = gpsTracker.getLatitude();
                    double longitude = gpsTracker.getLongitude();

                    String address = getCurrentAddress(latitude, longitude);
                    textView1.setText(address);

                    String phoneNumber = "여기에 -없이 전화번호 입력";

                    try {
                        SmsManager smsManager = SmsManager.getDefault();
                        smsManager.sendTextMessage(phoneNumber, null, address, null, null);
                        Toast.makeText(getApplicationContext(), "메시지 전송 완료", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "메시지 전송 실패", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }

                    return true;
                }
                case R.id.navigation_call:{
                    // 112 신고(전화)

                    return true;
                }
                case R.id.navigation_info:{
                    textView1.setText("info");
                    return true;
                }
            }
            return false;
        }
    }

    // ----------------------------------------------------- 센서값 읽어오기 -------------------------------------------------------
    private Interpreter getTfliteInterpreter(String modelPath) {
        try {
            return new Interpreter(loadModelFile(MainActivity.this, modelPath));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 모델을 읽어오는 함수로, 텐서플로 라이트 홈페이지에 있다.
    // MappedByteBuffer 바이트 버퍼를 Interpreter 객체에 전달하면 모델 해석을 할 수 있다.
    private MappedByteBuffer loadModelFile(Activity activity, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManger.registerListener(this, linearSensor, SensorManager.SENSOR_DELAY_UI);
        mSensorManger.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManger.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == linearSensor && num > 160) {
            ax = event.values[0];
            ay = event.values[1];
            az = event.values[2];

            Kalax=(float)mKalmanAccX.update(ax);
            Kalay=(float)mKalmanAccY.update(ay);
            Kalaz=(float)mKalmanAccZ.update(az);
        }

        if (event.sensor == gyroSensor && num > 160) {
            double gyroX = event.values[0];
            double gyroY = event.values[1];
            double gyroZ = event.values[2];
            gx = (float) gyroX;
            gy = (float) gyroY;
            gz = (float) gyroZ;

            Kalgx=(float)mKalmanGyroX.update(gx);
            Kalgy=(float)mKalmanGyroY.update(gy);
            Kalgz=(float)mKalmanGyroZ.update(gz);

            axque.enqueue(ax);
            ayque.enqueue(ay);
            azque.enqueue(az);
            gxque.enqueue(gx);
            gyque.enqueue(gy);
            gzque.enqueue(gz);

            if (axque.size() > 40) {
                axque.dequeue();
                ayque.dequeue();
                azque.dequeue();
                gxque.dequeue();
                gyque.dequeue();
                gzque.dequeue();
            }

            if (axque.size() == 40) {
                axmax = axque.max();
                aymax = ayque.max();
                azmax = azque.max();
                axmin = axque.min();
                aymin = ayque.min();
                azmin = azque.min();
                gxmax = gxque.max();
                gymax = gyque.max();
                gzmax = gzque.max();
                gxmin = gxque.min();
                gymin = gyque.min();
                gzmin = gzque.min();
                CVA = (float) Math.sqrt(Math.pow(Kalax, 2) + Math.pow(Kalay, 2) + Math.pow(Kalaz, 2));

                float[][][] input = new float[][][]{{{axmax, axmin, aymax, aymin, azmax, azmin, CVA, gxmax, gxmin, gymax, gymin, gzmax, gzmin}}};
                float[][][] output = new float[][][]{{{(float) 0.0, (float) 0.0, (float) 0.0, (float) 0.0, (float) 0.0, (float) 0.0}}};

                Interpreter tflite = getTfliteInterpreter("tensorModel_211205.tflite");
                tflite.run(input, output);

                float max = output[0][0][0];
                int activity = (int) 0;
                String[] activities = new String[]{"Sit", "Stand", "Walk", "Run", "StairUp", "StrDown"};

                for (int i = 0; i < output[0][0].length; i++) {
                    if (output[0][0][i] > max) {
                        max = output[0][0][i];
                        activity = i;
                    }
                }
                if(max<0.4) {
                    showDialog();
                }
            }
        }
        num = num + 1;
    }

    // Dialog popup
    public  void showDialog(){
        dialog.show();

        // 아니오 버튼
        Button noBtn = dialog.findViewById(R.id.noBtn);
        noBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 원하는 기능 구현
                dialog.dismiss(); // 다이얼로그 닫기
            }
        });
        // 네 버튼
        dialog.findViewById(R.id.yesBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 원하는 기능 구현
                finish();           // 앱 종료
            }
        });

        dHandler.postDelayed(dRunnable, 5000);
    }

    //Dialog delay
    private Runnable dRunnable=new Runnable() {
        @Override
        public void run() {
            dialog.dismiss();
        }
    };

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    // ---------------------------------------------------------- 주소값 읽어오기 -------------------------------------------------------------
    /*
     * ActivityCompat.requestPermissions를 사용하여 퍼미션 요청의 결과를 리턴받는 메소드
     */
    @Override
    public void onRequestPermissionsResult(int permsRequestCode, @NonNull String[] permissions, @NonNull int[] grandResults) {
        super.onRequestPermissionsResult(permsRequestCode, permissions, grandResults);

        // 요청 코드가 PERMISSIONS_REQUEST_CODE 이고, 요청한 퍼미션 개수만큼 수신된 경우
        if (permsRequestCode == PERMISSIONS_REQUEST_CODE && grandResults.length == REQUIRED_PERMISSIONS.length) {
            boolean check_result = true;

            // 모든 퍼미션을 허용했는지 체크
            for (int result : grandResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    check_result = false;
                    break;
                }
            }

            if (!check_result) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])
                        || ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[1])
                        || ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[2])) {
                    Toast.makeText(MainActivity.this, "권한이 거부되었습니다. 앱을 재실행하여 권한을 허용해주세요.", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    Toast.makeText(MainActivity.this, "권한이 거부되었습니다. 설정(앱 정보)에서 권한을 허용해야 합니다. ", Toast.LENGTH_LONG).show();
                }
            }
        }

    }

    // 런타임 퍼미션 처리
    void checkRunTimePermission(){

        if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED &&
                hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED &&
                hasSMSPermission == PackageManager.PERMISSION_GRANTED) {
            // 퍼미션을 가지고 있다면 위치 값을 가져올 수 있음
        }
        else {  // 퍼미션 요청이 허용되지 않았다면
            // i) 사용자가 과거에 퍼미션 거부를 한 적이 있는 경우, 퍼미션을 다시 요청. 요청 결과는 onRequestPermissionResult에서 수신됨.
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, REQUIRED_PERMISSIONS[0])) {
                Toast.makeText(MainActivity.this, "설정(앱 정보)에서 위치 및 SMS 권한을 허용해야 합니다. ", Toast.LENGTH_LONG).show();
            }
            // ii) 사용자가 퍼미션 거부를 한 적이 없는 경우, 바로 퍼미션 요청
            ActivityCompat.requestPermissions(MainActivity.this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
        }

    }

    // GeoCoder를 사용하여 위도&경도를 주소로 변환
    public String getCurrentAddress( double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        List<Address> addresses;

        try {
            addresses = geocoder.getFromLocation(
                    latitude,
                    longitude,
                    7);
        } catch (IOException ioException) {     //네트워크 문제
            Toast.makeText(this, "주소 서비스 사용불가", Toast.LENGTH_LONG).show();
            return "주소 서비스 사용불가";
        } catch (IllegalArgumentException illegalArgumentException) {
            Toast.makeText(this, "잘못된 GPS 좌표입니다.", Toast.LENGTH_LONG).show();
            return "잘못된 GPS 좌표입니다.";
        }

        if (addresses == null || addresses.size() == 0) {
            Toast.makeText(this, "해당하는 주소가 없습니다.", Toast.LENGTH_LONG).show();
            return "해당하는 주소가 없습니다.";
        }

        Address address = addresses.get(0);

        return address.getAddressLine(0).toString()+"\n";
    }

    // GPS를 활성화하기 위한 메소드(1)
    private void showDialogForLocationServiceSetting() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("위치 서비스 비활성화");
        builder.setMessage("앱을 사용하기 위해서는 위치 사용 권한이 필요합니다.");
        builder.setCancelable(true);

        builder.setPositiveButton("설정", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                Intent callGPSSettingIntent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(callGPSSettingIntent, GPS_ENABLE_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }

    // GPS를 활성화하기 위한 메소드(2)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case GPS_ENABLE_REQUEST_CODE:
                //사용자가 GPS 활성화했는지 검사
                if (checkLocationServicesStatus()) {
                    if (checkLocationServicesStatus()) {
                        Log.d("@@@", "onActivityResult : GPS 활성화 되있음");
                        checkRunTimePermission();
                        return;
                    }
                }
                break;
        }
    }

    // GPS를 활성화하기 위한 메소드(3)
    public boolean checkLocationServicesStatus() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

}