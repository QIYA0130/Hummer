package com.didi.hummer.devtools.ws;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 开发调试模式下的WebSocket管理类，负责和CLI建立连接，带有重连功能
 *
 * Created by XiaoFeng on 2021/4/22.
 */
public class WebSocketManager {

    public interface WSMsgListener {
        void onMsgReceived(String msg);
    }

    private static final int RECONNECT_DELAY_MS = 2000;

    private static OkHttpClient client;
    private WebSocket webSocket;
    private String mWsUrl;
    private Handler mHandler;
    private boolean mClosed;
    private boolean mIsReconnectWaiting;

    public WebSocketManager() {
        mHandler = new Handler(Looper.getMainLooper());
        if (client == null) {
            client = new OkHttpClient();
        }
    }

    public void connect(String url, WSMsgListener listener) {
        mWsUrl = toWSUrl(url);
        doConnect(mWsUrl, listener);
    }

    private void doConnect(String url, WSMsgListener listener) {
        if (TextUtils.isEmpty(url)) {
            return;
        }

        okhttp3.Request request = new Request.Builder()
                .url(url)
                .build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(okhttp3.WebSocket webSocket, Response response) {
                WebSocketManager.this.webSocket = webSocket;
            }

            @Override
            public void onClosed(okhttp3.WebSocket webSocket, int code, String reason) {
                if (!mClosed) {
                    reconnect(listener);
                }
            }

            @Override
            public void onFailure(okhttp3.WebSocket webSocket, Throwable t, Response response) {
                t.printStackTrace();
                if (!mClosed) {
                    reconnect(listener);
                }
            }

            @Override
            public void onMessage(okhttp3.WebSocket webSocket, String text) {
                mHandler.post(() -> {
                    if (listener != null) {
                        listener.onMsgReceived(text);
                    }
                });
            }
        });
    }

    private void reconnect(WSMsgListener listener) {
        if (mClosed || mIsReconnectWaiting) {
            return;
        }
        mIsReconnectWaiting = true;
        mHandler.postDelayed(() -> {
            if (!mClosed) {
                doConnect(mWsUrl, listener);
            }
            mIsReconnectWaiting = false;
        }, RECONNECT_DELAY_MS);
    }

    public void sendMsg(String msg) {
        if (webSocket != null) {
            webSocket.send(msg);
        }
    }

    public void close() {
        mClosed = true;
        if (webSocket != null) {
            try {
                webSocket.close(1000, "End of session");
            } catch (Exception e) {
                // swallow, no need to handle it here
            }
            webSocket = null;
        }
    }

    /**
     * 把http的url转换成websocket的url
     *
     * @param url
     * @return
     */
    private String toWSUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }

        url = url.toLowerCase();
        if (!url.startsWith("http://")) {
            if (url.startsWith("ws://")) {
                return url;
            }
            return null;
        }

        Uri uri = Uri.parse(url);

        return "ws://" + uri.getAuthority() + "/proxy/native";
    }
}
