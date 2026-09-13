package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.content.res.TypedArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.adapter.DeviceAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ScanTask;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.WebDav;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class SyncDialog extends BaseBottomSheetDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener {

    private final OkHttpClient client;
    private final TypedArray mode;

    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private ScanTask scanTask;
    private String type;

    public SyncDialog() {
        scanTask = new ScanTask(this);
        // Direct connections only: device addresses are LAN IP literals, they must never be
        // resolved through DoH or routed through the user's proxy.
        client = OkHttp.client(Constant.TIMEOUT_SYNC).newBuilder().dns(Dns.SYSTEM).proxy(java.net.Proxy.NO_PROXY).build();
        mode = ResUtil.getTypedArray(R.array.cast_mode);
    }

    public static SyncDialog create() {
        return new SyncDialog();
    }

    public SyncDialog history() {
        return type("history");
    }

    public SyncDialog keep() {
        return type("keep");
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof SyncDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private SyncDialog type(String type) {
        this.type = type;
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.mode.setVisibility(View.VISIBLE);
        setRecyclerView();
        getDevice();
        setMode();
    }

    @Override
    protected void initEvent() {
        binding.mode.setOnClickListener(v -> onMode());
        binding.scan.setOnClickListener(v -> onScan());
        binding.refresh.setOnClickListener(v -> onRefresh());
        binding.cloud.setOnClickListener(v -> onCloud());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(false);
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
    }

    private void getDevice() {
        adapter.setItems(Device.getAll(), () -> {
            if (adapter.getItemCount() == 0) onRefresh();
            else binding.recycler.setVisibility(View.VISIBLE);
        });
    }

    private void setMode() {
        int index = Setting.getSyncMode();
        binding.mode.setImageResource(mode.getResourceId(index, 0));
        binding.mode.setTag(String.valueOf(index));
    }

    private void onMode() {
        int index = Setting.getSyncMode();
        Setting.putSyncMode(index = index == mode.length() - 1 ? 0 : ++index);
        binding.mode.setImageResource(mode.getResourceId(index, 0));
        binding.mode.setTag(String.valueOf(index));
    }

    private void onScan() {
        launcher.launch(new Intent(requireActivity(), ScanActivity.class));
    }

    private void onRefresh() {
        adapter.clear(() -> {
            Device.delete();
            scanTask.start();
            binding.recycler.setVisibility(View.GONE);
        });
    }

    private void onSuccess() {
        dismiss();
    }

    @Override
    public void onFind(Device device) {
        binding.recycler.setVisibility(View.VISIBLE);
        adapter.sort(device);
    }

    @Override
    public void onScanStart() {
        binding.hint.setVisibility(View.VISIBLE);
        binding.hint.setText(R.string.device_scan_scanning);
    }

    @Override
    public void onScanEnd(int count) {
        binding.hint.setVisibility(View.VISIBLE);
        binding.hint.setText(count == 0 ? getString(R.string.device_scan_empty) : getString(R.string.device_scan_found, count));
    }

    @Override
    public void onItemClick(Device item) {
        // Mode 3 is a true pull: fetch the peer's data and import it here, which also works
        // when the peer cannot reach us back (emulator behind NAT).
        if (binding.mode.getTag().toString().equals("3")) pull(item);
        else send(item, binding.mode.getTag().toString(), false);
    }

    @Override
    public boolean onLongClick(Device item) {
        String mode = binding.mode.getTag().toString();
        if (mode.equals("0") || mode.equals("3")) return false;
        send(item, mode, true);
        return true;
    }

    private void send(Device item, String mode, boolean force) {
        String url = String.format(Locale.getDefault(), "%s/action?do=sync&mode=%s&type=%s%s", item.getIp(), mode, type, force ? "&force=true" : "");
        Runnable request = () -> OkHttp.newCall(client, url, buildBody()).enqueue(getCallback());
        if (type.equals("history")) Task.executeSerial(request);
        else request.run();
    }

    private FormBody buildBody() {
        if (type.equals("history")) {
            Config config = Config.vod();
            FormBody.Builder body = new FormBody.Builder();
            body.add("device", Device.get().toString());
            body.add("config", config.toString());
            body.add("targets", App.gson().toJson(History.get(config.getId())));
            return body.build();
        } else {
            FormBody.Builder body = new FormBody.Builder();
            body.add("device", Device.get().toString());
            body.add("targets", App.gson().toJson(Keep.getVod()));
            body.add("configs", App.gson().toJson(Config.findUrls()));
            return body.build();
        }
    }

    /**
     * Fetches the peer's export and replays it into our own server as a sync import, reusing
     * the exact merge logic a normal push would take.
     */
    private void pull(Device item) {
        Task.execute(() -> {
            boolean done = false;
            try (Response response = OkHttp.newCall(client, item.getIp().concat("/action?do=export&type=" + type), "pull").execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful() || body.isEmpty()) throw new IllegalStateException(response.message());
                done = importPayload(body);
            } catch (Exception e) {
                App.post(() -> Notify.show(e.getMessage()));
            }
            if (done) App.post(() -> {
                Notify.show(R.string.device_pull_done);
                onSuccess();
            });
        });
    }

    /** Feeds a sync payload (same shape as the server export) into our own server for import. */
    private boolean importPayload(String body) {
        try {
            JsonObject data = App.gson().fromJson(body, JsonObject.class);
            FormBody.Builder form = new FormBody.Builder();
            if (type.equals("history")) {
                if (data.get("config") == null || data.get("targets") == null) return false;
                form.add("config", data.get("config").toString());
                form.add("targets", data.get("targets").toString());
            } else {
                if (data.get("targets") == null || data.get("configs") == null) return false;
                form.add("targets", data.get("targets").toString());
                form.add("configs", data.get("configs").toString());
            }
            // Loopback import on 127.0.0.1: no dependency on which NIC Util.getIp() picked.
            String url = Server.get().getAddress(true).concat("/action?do=sync&mode=1&type=" + type);
            try (Response res = OkHttp.newCall(client, url, form.build()).execute()) {
                return res.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** Builds the cloud payload: same shape the server export endpoint serves. */
    private String buildExport() {
        JsonObject data = new JsonObject();
        if (type.equals("keep")) {
            data.add("targets", App.gson().toJsonTree(Keep.getVod()));
            data.add("configs", App.gson().toJsonTree(Config.findUrls()));
        } else {
            Config config = Config.vod();
            data.add("config", App.gson().toJsonTree(config));
            data.add("targets", App.gson().toJsonTree(History.get(config.getId())));
        }
        return App.gson().toJson(data);
    }

    /**
     * Cloud sync through WebDAV: both devices configure the same account, one uploads, the
     * other downloads and merges. Works across any topology (emulator NAT, different Wi-Fi,
     * remote networks) because neither device needs to reach the other directly.
     */
    private void onCloud() {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(requireActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad / 2, pad, 0);
        EditText url = new EditText(requireActivity());
        url.setHint(R.string.cloud_url_hint);
        url.setInputType(EditorInfo.TYPE_TEXT_VARIATION_URI);
        url.setSingleLine(true);
        url.setText(Setting.getWebDavUrl());
        EditText user = new EditText(requireActivity());
        user.setHint(R.string.cloud_user_hint);
        user.setSingleLine(true);
        user.setText(Setting.getWebDavUser());
        EditText pass = new EditText(requireActivity());
        pass.setHint(R.string.cloud_pass_hint);
        pass.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setSingleLine(true);
        pass.setText(Setting.getWebDavPass());
        TextView tip = new TextView(requireActivity());
        tip.setText(R.string.cloud_tip);
        tip.setTextSize(12);
        tip.setPadding(pad / 4, pad / 2, pad / 4, 0);
        root.addView(url);
        root.addView(user);
        root.addView(pass);
        root.addView(tip);
        AlertDialog dialog = new AlertDialog.Builder(requireActivity()).setTitle(R.string.cloud_title).setView(root).setPositiveButton(R.string.cloud_upload, (d, w) -> cloud(true, url, user, pass)).setNeutralButton(R.string.cloud_download, (d, w) -> cloud(false, url, user, pass)).setNegativeButton(android.R.string.cancel, null).create();
        // Save whatever is typed no matter how the dialog closes - cancel/back included,
        // so a password entered "just to store it" is never silently dropped.
        dialog.setOnDismissListener(d -> {
            Setting.putWebDavUrl(url.getText().toString().trim());
            Setting.putWebDavUser(user.getText().toString().trim());
            Setting.putWebDavPass(pass.getText().toString().trim());
        });
        dialog.show();
    }

    private void cloud(boolean upload, EditText url, EditText user, EditText pass) {
        Setting.putWebDavUrl(url.getText().toString().trim());
        Setting.putWebDavUser(user.getText().toString().trim());
        Setting.putWebDavPass(pass.getText().toString().trim());
        if (!WebDav.isConfigured()) {
            Notify.show(R.string.cloud_bad_config);
            return;
        }
        String file = "listendtv/sync-" + type + ".json";
        Task.execute(() -> {
            try {
                if (upload) {
                    WebDav.put(file, buildExport());
                    App.post(() -> Notify.show(R.string.cloud_up_ok));
                } else {
                    String body = WebDav.get(file);
                    if (importPayload(body)) App.post(() -> {
                        Notify.show(R.string.cloud_down_ok);
                        onSuccess();
                    });
                    else App.post(() -> Notify.show(R.string.cloud_bad_data));
                }
            } catch (Exception e) {
                String msg = String.valueOf(e.getMessage());
                App.post(() -> {
                    if (msg.contains("401")) Notify.show(R.string.cloud_bad_auth);
                    else Notify.show(msg);
                });
            }
        });
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                App.post(() -> onSuccess());
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                App.post(() -> Notify.show(e.getMessage()));
            }
        };
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        scanTask.stop();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) scanTask.start(result.getData().getStringExtra("address"));
    });
}
