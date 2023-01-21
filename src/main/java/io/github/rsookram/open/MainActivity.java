package io.github.rsookram.open;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class MainActivity extends Activity {

    // These need to be relative to the root of external storage
    private static final String[] DEFAULT_DIRECTORIES = {
    };

    private static final String EXTRA_PATH = "path";
    private static final int REQUEST_PERMISSION = 1;

    static Intent newIntent(Context context, String path) {
        return new Intent(context, MainActivity.class)
                .putExtra(EXTRA_PATH, path);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        if (Environment.isExternalStorageManager()) {
            setup();
        } else {
            startActivityForResult(
                    new Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.fromParts("package", getPackageName(), null)
                    ),
                    REQUEST_PERMISSION
            );
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PERMISSION && Environment.isExternalStorageManager()) {
            setup();
        }
    }

    private void setup() {
        String path = getIntent().getStringExtra(EXTRA_PATH);
        File[] files;
        if (path != null) {
            // TODO: Verify that path is valid
            files = new File(path).listFiles((d, name) -> !name.startsWith("."));
        } else {
            File externalStorageDirectory = Environment.getExternalStorageDirectory();
            files = Arrays.stream(DEFAULT_DIRECTORIES)
                    .map(dir -> new File(externalStorageDirectory, dir))
                    .filter(File::exists)
                    .toArray(File[]::new);
        }

        if (files == null) {
            files = new File[]{};
        }

        Arrays.sort(
                files,
                Comparator.comparing(File::isDirectory).reversed().thenComparing(File::getName)
        );

        ListView listView = findViewById(R.id.list);
        FileAdapter adapter = new FileAdapter(this, files);
        listView.setAdapter(adapter);

        // Open selected item when enter is pressed (for hardware keyboards)
        listView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_ENTER || event.getAction() != KeyEvent.ACTION_UP) {
                return false;
            }

            File selectedItem = (File) listView.getSelectedItem();
            if (selectedItem == null) {
                return false;
            }

            openFile(v.getContext(), selectedItem);

            return true;
        });
    }

    private static void openFile(Context context, File file) {
        if (file.isDirectory()) {
            context.startActivity(MainActivity.newIntent(context, file.getPath()));
        } else {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    file
            );
            String mimeType = getMimeType(file.getName());
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;

            if (!mimeType.startsWith("video/")) {
                flags |= Intent.FLAG_ACTIVITY_NEW_TASK;
            }

            context.startActivity(
                    new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, mimeType)
                            .addFlags(flags)
            );
        }
    }

    private static String getMimeType(String name) {
        String[] strings = name.split("\\.");
        if (strings.length == 1) {
            return "application/octet-stream";
        }

        String extension = strings[strings.length - 1];
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private static class FileAdapter extends ArrayAdapter<File> {

        FileAdapter(Context context, File[] files) {
            super(context, -1 /* unused */, files);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = parent.getContext();

            TextView view = (TextView) convertView;
            if (view == null) {
                view = (TextView) LayoutInflater.from(context)
                        .inflate(R.layout.item_file, parent, false);
            }

            File file = getItem(position);

            String name = file.getName();
            view.setText(name);

            view.setOnClickListener(v -> openFile(context, file));

            return view;
        }
    }
}
