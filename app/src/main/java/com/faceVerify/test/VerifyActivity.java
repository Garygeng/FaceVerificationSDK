package com.faceVerify.test;

import static com.AI.FaceVerify.verify.VerifyStatus.ALIVE_DETECT_RESULT_ENUM.ACTION_FAILED;
import static com.AI.FaceVerify.verify.VerifyStatus.ALIVE_DETECT_RESULT_ENUM.ACTION_NO_FACE_DETECT;
import static com.AI.FaceVerify.verify.VerifyStatus.ALIVE_DETECT_RESULT_ENUM.ACTION_OK;
import static com.AI.FaceVerify.verify.VerifyStatus.ALIVE_DETECT_RESULT_ENUM.ACTION_TIME_OUT;
import static com.AI.FaceVerify.verify.VerifyStatus.ALIVE_DETECT_TYPE_ENUM.NOD_HEAD;
import static com.AI.FaceVerify.verify.VerifyStatus.ALIVE_DETECT_TYPE_ENUM.SHAKE_HEAD;

import static com.AI.FaceVerify.verify.VerifyStatus.ALIVE_DETECT_TYPE_ENUM.BLINK;
import static com.AI.FaceVerify.verify.VerifyStatus.ALIVE_DETECT_TYPE_ENUM.OPEN_MOUSE;
import static com.AI.FaceVerify.verify.VerifyStatus.ALIVE_DETECT_TYPE_ENUM.SMILE;
import static com.faceVerify.test.FaceApplication.BASE_FACE_KEY;
import static com.faceVerify.test.FaceApplication.CACHE_BASE_FACE_DIR;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import com.AI.FaceVerify.graphic.GraphicOverlay;
import com.AI.FaceVerify.verify.FaceDetectorUtils;
import com.AI.FaceVerify.verify.FaceProcessBuilder;
import com.AI.FaceVerify.verify.ProcessCallBack;
import com.AI.FaceVerify.utils.AiUtil;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * 检验超时周末有空添加，可以根据Demo 结合自身进行业务定制UX，请先了解人脸识别基础
 *
 * MFC: https://arxiv.org/pdf/1804.07573.pdf
 * BAD：https://ai.baidu.com/ai-doc/FACE/Bkp6nusr3 (参考性能指标描述等)
 */
public class VerifyActivity extends AppCompatActivity {

    private TextView resultTextView, tipsTextView;
    private GraphicOverlay mGraphicOverlay; //遮罩层，仅仅用于调试用

    private boolean isPass = false;
    private Bitmap baseBitmap; //底片Bitmap
    private FaceDetectorUtils faceDetectorUtils = new FaceDetectorUtils();


    private String yourUniQueFaceId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify);

        tipsTextView = findViewById(R.id.tips_view);
        resultTextView = findViewById(R.id.result_text_view);
//        mGraphicOverlay = findViewById(R.id.graphic_overlay);
        yourUniQueFaceId = getIntent().getStringExtra(BASE_FACE_KEY);

        //可以自己录一张人脸底片，后期 底片不用 Bitmap ,加密使用
        File file = new File(CACHE_BASE_FACE_DIR,yourUniQueFaceId);

        baseBitmap = AiUtil.compressPath(VerifyActivity.this, Uri.fromFile(file));

        // 活体检测的使用需要你发送邮件申请，简要描述App名称，包名和功能简介到 anylife.zlb@gmail.com
        FaceProcessBuilder faceProcessBuilder = new FaceProcessBuilder.Builder(this)
                // threshold 相似度门槛值后期支持设置
                .setBaseBitmap(baseBitmap)          //底片
//                .setGraphicOverlay(mGraphicOverlay) //遮罩层，人脸模型画面演示。正式环境不需要配置
                .setLiveCheck(true)  //是否需要活体检测，需要发送邮件，详情参考ReadMe
                .setProcessCallBack(new ProcessCallBack() {
                    @Override
                    public void onCompleted(boolean isMatched) {
                        runOnUiThread(() -> {
                            if (isMatched) {
                                isPass = true;
                                resultTextView.setText("核验已通过，与底片为同一人！ ");
                                resultTextView.setBackgroundColor(getResources()
                                        .getColor(R.color.green));


                                new AlertDialog.Builder(VerifyActivity.this)
                                        .setMessage("核验已通过，与底片为同一人！")
                                        .setPositiveButton("知道了",
                                                (dialog1, which) -> {
                                                    VerifyActivity.this.finish();
                                                })
                                        .show();


                            } else {
                                isPass = false;
                                resultTextView.setText("核验不通过，与底片不符！ ");
                                resultTextView.setBackgroundColor(getResources()
                                        .getColor(R.color.red));


                                new AlertDialog.Builder(VerifyActivity.this)
                                        .setMessage("核验不通过，与底片不符！ ")
                                        .setPositiveButton("知道了",
                                                (dialog1, which) -> {
                                                    VerifyActivity.this.finish();
                                                })
                                        .show();

                            }
                        });
                    }

                    @Override
                    public void onFailed(int code) {

                    }

                    @Override
                    public void onProcessTips(int actionCode) {
                        showAliveDetectTips(actionCode);
                    }
                })
                .create();

        faceDetectorUtils.setDetectorParams(faceProcessBuilder);

        initCameraXAnalysis();
    }


    /**
     * 根据业务和设计师UI交互修改你的 UI
     */
    private void showAliveDetectTips(int actionCode) {
        runOnUiThread(() -> {
            switch (actionCode) {

                case ACTION_TIME_OUT:
                    new android.app.AlertDialog.Builder(VerifyActivity.this)
                            .setMessage("检测超时了！")
                            .setPositiveButton("再来一次",
                                    (dialog1, which) -> {
                                        //Demo 只是把每种状态抛出来，用户可以自己根据需求改造

                                        faceDetectorUtils.retryVerify();
                                    })
                            .show();
                    break;

                case ACTION_NO_FACE_DETECT:
                    tipsTextView.setText("画面没有检测到人脸");
                    break;

                case ACTION_FAILED:
                    tipsTextView.setText("活体检测失败了");
                    break;
                case ACTION_OK:
                    tipsTextView.setText("已经完成活体检测");

                    break;
                case OPEN_MOUSE:
                    tipsTextView.setText("请张嘴");
                    break;

                case SMILE:
                    tipsTextView.setText("请微笑");
                    break;

                case BLINK:
                    tipsTextView.setText("请轻眨眼");
                    break;

                case SHAKE_HEAD:
                    tipsTextView.setText("请缓慢左右摇头");
                    break;

                case NOD_HEAD:
                    tipsTextView.setText("请缓慢上下点头");
                    break;

            }
        });
    }


    /**
     * 初始化相机,使用CameraX 结合CNN
     */
    public void initCameraXAnalysis() {
        PreviewView previewView = findViewById(R.id.previewView);

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        //图像预览和摄像头原始数据回调 暴露，以便后期格式转换和人工智障处理
        //图像编码默认格式 YUV_420_888。
        cameraProviderFuture.addListener(() -> {
            try {
                // Camera provider is now guaranteed to be available
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up the view finder use case to display camera preview
                Preview preview = new Preview.Builder().build();

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                //CameraX 可通过 setOutputImageFormat(int) 支持 YUV_420_888
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(),
                        new ImageAnalysis.Analyzer() {
                            @SuppressLint("UnsafeOptInUsageError")
                            @Override
                            public void analyze(@NonNull ImageProxy imageProxy) {
                                if (imageProxy.getFormat() != ImageFormat.YUV_420_888) {
                                    throw new IllegalArgumentException("Invalid image format");
                                }

                                if (!isPass) {
                                    faceDetectorUtils.goVerify(imageProxy);
                                }

                                imageProxy.close();
                            }
                        });


                // Choose the camera by requiring a lens facing
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                // Attach use cases to the camera with the same lifecycle owner
                Camera camera = cameraProvider.bindToLifecycle(
                        ((LifecycleOwner) this),
                        cameraSelector,
                        preview, imageAnalysis);

                // Connect the preview use case to the previewView
                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider());

            } catch (InterruptedException | ExecutionException e) {
                // Currently no exceptions thrown. cameraProviderFuture.get()
                // shouldn't block since the listener is being called, so no need to
                // handle InterruptedException.
            }

        }, ContextCompat.getMainExecutor(this));
    }


    /**
     * 资源释放
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        faceDetectorUtils = null;
    }


}