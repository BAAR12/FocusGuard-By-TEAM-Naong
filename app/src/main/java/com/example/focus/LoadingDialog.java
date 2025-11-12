package com.example.focus;

import android.app.Activity;
import android.view.LayoutInflater;
import androidx.appcompat.app.AlertDialog;

public class LoadingDialog {

    private Activity activity;
    private AlertDialog dialog;

    public LoadingDialog(Activity myActivity) {
        activity = myActivity;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AlertDialogTheme); // Use our custom theme

        // Inflate the custom layout
        LayoutInflater inflater = activity.getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.dialog_loading, null));
        builder.setCancelable(false); // User can't cancel it

        dialog = builder.create();

        // Make the background of the dialog transparent (so only the card is visible)
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.show();
    }

    public void hide() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}