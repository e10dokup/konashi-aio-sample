package dokup.xyz.konashiloggingtest;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.uxxu.konashi.lib.Konashi;
import com.uxxu.konashi.lib.KonashiListener;
import com.uxxu.konashi.lib.KonashiManager;

import org.jdeferred.DoneCallback;
import org.jdeferred.DonePipe;
import org.jdeferred.Promise;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import info.izumin.android.bletia.BletiaException;

public class MainActivity extends AppCompatActivity {
    private final MainActivity self = this;

    //インスタンス、変数宣言
    private Timer mTimer = null;
    private KonashiManager mKonashiManager;
    //Android 端末のフォルダ直下のパス取得。
    private String sdPath = Environment.getExternalStorageDirectory() + "/";
    //データ保存用ファイル名付ける。
    private String sdFileName = "data_save.txt";
//    //30 分間データ保存できる配列を用意する。
//    private int saveArray1[] = new int[18000];
//    private int saveArray2[] = new int[18000];
//    //繰返し回数(何回記録したのか確認する為)の配列を用意する。
//    private int repeatTimeArray[] = new int[18000];
    // 可変長リストを使いましょう．
    // データ保存用リスト
    private List<Integer> saveList1 = new ArrayList<>();
    private List<Integer> saveList2 = new ArrayList<>();
    // クリエイ回数記録用リスト
    private List<Integer> repeatTimeList = new ArrayList<>();
    private int repeatTime=0;

    private Button mFindButton;
    private Button mStartReadButton;
    private Button mFinishReadButton;
    private TextView mRepeatTimeTextView;
    private TextView mValue1TextView;
    private TextView mValue2TextView;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFindButton = (Button) findViewById(R.id.btn_find);
        mStartReadButton = (Button) findViewById(R.id.btn_read);
        mFinishReadButton = (Button) findViewById(R.id.btn_finish);
        mRepeatTimeTextView = (TextView) findViewById(R.id.text_repeat_time);
        mValue1TextView = (TextView) findViewById(R.id.text_value_1);
        mValue2TextView = (TextView) findViewById(R.id.text_value_2);

        mKonashiManager = new KonashiManager(this);

        //ボタンにイベント登録
        //btn_find ボタン押すと、KONASHI を探す。
        mFindButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mKonashiManager.find(self);
            }
        });
        //btn_read ボタン押すと、計測(100ms 周期で AIO0,1 の電圧計測)開始。
        mStartReadButton.setOnClickListener(new StartReadButtonClickListener());
        //btn_finish ボタン押すと、計測停止&ファイル記録。
        mFinishReadButton.setOnClickListener(new FinishReadButtonClickListener());

        mKonashiManager.addListener(new KonashiListener() {
            @Override
            public void onConnect(KonashiManager manager) {
                Toast.makeText(MainActivity.this, "接続しました。", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onDisconnect(KonashiManager manager) {
            }

            @Override
            public void onError(KonashiManager manager, BletiaException e) {
            }

            @Override
            public void onUpdatePioOutput(KonashiManager manager, int value) {
            }

            @Override
            public void onUpdateUartRx(KonashiManager manager, byte[] value) {
            }

            @Override
            public void onUpdateBatteryLevel(KonashiManager manager, int level) {
            }

            @Override
            public void onUpdateSpiMiso(KonashiManager manager, byte[] value) {
            }
        });
    }

    //btn_read ボタン押したときのイベント(100ms 周期で AIO0,1 の電圧計測開始)。
    private class StartReadButtonClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            //Timer クラスを使用し、100ms 周期で電圧計測。
            mTimer = new Timer(true);
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    repeatTime++;
                    repeatTimeList.add(repeatTime);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mRepeatTimeTextView.setText(String.valueOf(repeatTime));
                        }
                    });

                    // AIO0電圧計速
                    // DoneCallbackではなくDonePipeを使うことで.thenを利用して処理をつなげます．
                    mKonashiManager.analogRead(Konashi.AIO0)
                            .then(new DonePipe<Integer, Integer, BletiaException, Void>() {
                                @Override
                                public Promise<Integer, BletiaException, Void> pipeDone(final Integer result) {
                                    saveList1.add(result);
                                    // 取得した電圧値をリストに格納
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mValue1TextView.setText(String.valueOf(result));
                                        }
                                    });
                                    return mKonashiManager.analogRead(Konashi.AIO1);
                                }
                            })
                            .done(new DoneCallback<Integer>() {
                                @Override
                                public void onDone(final Integer result) {
                                    saveList2.add(result);
                                    // 取得した電圧値をリストに格納
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mValue2TextView.setText(String.valueOf(result));
                                        }
                                    });
                                }
                            });
                }
            }, 1000, 400); // 100ms-300msの短時間ループではAIOの処理が追いつきません．
        }
    }

    //btn_finish ボタン押したときのイベント(計測停止&ファイル記録)。
    private class FinishReadButtonClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            //タイマー(繰返し計測)停止
            mTimer.cancel();
            //データ書き込み
            try {
                FileWriter fw = new FileWriter(sdPath + sdFileName, true);
                for (int i = 0; i < repeatTime; i++){
                    fw.write(repeatTimeList.get(i) + ",ch1," + saveList1.get(i) + ",ch2," + saveList2.get(i) + "\n");
                }
                fw.flush();
                fw.close();
                Toast.makeText(MainActivity.this, "保存完了", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "保存失敗", Toast.LENGTH_LONG).show();
            }
        }
    }
}
