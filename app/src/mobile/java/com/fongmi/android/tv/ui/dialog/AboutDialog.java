package com.fongmi.android.tv.ui.dialog;

import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.databinding.DialogAboutBinding;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

public class AboutDialog extends BaseAlertDialog {

    private DialogAboutBinding binding;

    public static AboutDialog create() {
        return new AboutDialog();
    }

    public AboutDialog show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogAboutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.setting_about).setView(getBinding().getRoot()).setPositiveButton(R.string.about_check, null).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        binding.versionText.setText(ResUtil.getString(R.string.about_current, BuildConfig.VERSION_NAME));
        loadLatest();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(this::onCheckUpdate);
    }

    private void onCheckUpdate(View view) {
        view.setEnabled(false);
        Updater.create().force().start(requireActivity());
        App.post(() -> {
            try {
                if (isAdded() && getDialog() != null) view.setEnabled(true);
            } catch (Exception ignored) {
            }
        }, 3000);
    }

    private void loadLatest() {
        Task.execute(() -> {
            try {
                JSONObject object = Github.fetchLatest();
                String tag = object.optString("tag_name");
                String body = object.optString("body");
                App.post(() -> {
                    if (!isAdded()) return;
                    binding.latestText.setText(ResUtil.getString(R.string.about_latest, tag));
                    binding.notesText.setText(body.isEmpty() ? ResUtil.getString(R.string.about_no_notes) : body.trim());
                });
            } catch (Exception e) {
                App.post(() -> {
                    if (!isAdded()) return;
                    binding.latestText.setText(ResUtil.getString(R.string.about_latest_fail));
                });
            }
        });
    }
}
