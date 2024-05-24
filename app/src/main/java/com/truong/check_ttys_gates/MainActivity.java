package com.truong.check_ttys_gates;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.truong.check_ttys_gates.databinding.ActivityMainBinding;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import android_serialport_api.SerialPort;
import android_serialport_api.SerialPortFinder;
import tp.xmaihh.serialport.SerialHelper;
import tp.xmaihh.serialport.bean.ComBean;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 2;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 3;
    private static final String PORT_NAME = "/dev/ttyS5"; // Đường dẫn đến cổng serial, có thể là /dev/ttyS0, /dev/ttyS1, vv.
    private SerialPort serialPort;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread readThread;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        GridLayout gridLayout = findViewById(R.id.gridLayout);

        // Tạo và thêm các button vào GridLayout
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 10; j++) {
                final int position = i * 10 + j + 1;
                Button button = new Button(this);
                button.setText("Button " + position);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Xử lý sự kiện khi button được nhấn
                        int result = 0xFF - position;
                        String hexResult = Integer.toHexString(result).toUpperCase();
                        String hexPosition = Integer.toHexString(position).toUpperCase();
                        hexPosition = hexPosition.length() == 1 ? "0" + hexPosition : hexPosition;
                        hexResult = hexResult.length() == 1 ? "0" + hexResult : hexResult; // Đảm bảo dạng hex có 2 ký tự
                        String hexButton = String.format(hexResult);
                        sendHexString("00ff" + hexPosition + hexButton + "aa55");
                    }
                });
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.rowSpec = GridLayout.spec(i);
                params.columnSpec = GridLayout.spec(j);
                gridLayout.addView(button, params);
            }
        }
        binding.searchBtn.setOnClickListener(v -> {
            binding.listGate.setText(getListOfTtyS().toString());
        });

        binding.enterBtn.setOnClickListener(v -> {
            sendHexString(binding.sendData.getText().toString().trim());

        });
        binding.turnOnLed.setOnClickListener(v -> {
            sendHexString("00ffdd22aa55");
        });
        binding.turnOffLed.setOnClickListener(v -> {
            sendHexString("00ffdd2255aa");
        });
        binding.checkAllSlot.setOnClickListener(v -> {
            sendHexString("00ff659a55aa");
        });
        binding.merge12Slot.setOnClickListener(v -> {
            sendHexString("00ffca3501fe");
        });
        binding.removeMerge12Slot.setOnClickListener(v -> {
            sendHexString("00ffc93601fe");
        });
        binding.removeAllMerge.setOnClickListener(v -> {
            sendHexString("00ffcb3455aa");
        });
        binding.checkCloseOpen.setOnClickListener(v -> {
            sendHexString("00ffdf2055aa");
        });
        binding.checkAllMotor.setOnClickListener(v -> {
            sendHexString("00ff758a55aa");
        });
        try {
            serialPort = new SerialPort(new File("/dev/ttyS5"), 9600, 1, 8, 0, 0, 0);
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
            startReadThread();
        } catch (IOException e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }

    private void startReadThread() {
        readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] buffer = new byte[1024];
                    int size;
                    while (!Thread.currentThread().isInterrupted()) {
                        size = inputStream.read(buffer);
                        if (size > 0) {
                            // Chuyển đổi dữ liệu sang dạng hex
                            StringBuilder hexString = new StringBuilder();
                            for (int i = 0; i < size; i++) {
                                String hex = Integer.toHexString(buffer[i] & 0xFF);
                                if (hex.length() == 1) {
                                    hexString.append('0');
                                }
                                hexString.append(hex).append(' ');
                            }
                            String receivedDataHex = hexString.toString().toUpperCase().trim();

                            // Log dữ liệu dạng hex
                            Log.d(TAG, "Received data (hex): " + receivedDataHex);
                            if (!receivedDataHex.isEmpty()){
                                binding.receiveData.setText("");
                                binding.receiveData.setText(receivedDataHex);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading from serial port: " + e.getMessage());
                }
            }
        });
        readThread.start();
    }


    public List<String> getListOfTtyS() {
        List<String> ttySList = new ArrayList<>();

        // Đường dẫn tới thư mục chứa các cổng ttyS trên thiết bị Android
        File devDirectory = new File("/dev");

        // Liệt kê tất cả các tệp trong thư mục /dev
        File[] files = devDirectory.listFiles();

        // Duyệt qua danh sách các tệp để tìm các tệp ttyS
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith("ttyS")) {
                    ttySList.add(file.getAbsolutePath());
                }
            }
        }

        return ttySList;
    }
    // Phương thức để gửi một chuỗi hex qua cổng serial
    public void sendHexString(String hexString) {
        try {
            byte[] hexBytes = hexStringToByteArray(hexString);
            FileOutputStream fos = new FileOutputStream(new File(PORT_NAME));
            fos.write(hexBytes);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Phương thức tiện ích để chuyển đổi một chuỗi hex thành một mảng byte
    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
