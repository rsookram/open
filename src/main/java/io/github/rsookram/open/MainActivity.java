package io.github.rsookram.open;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

public class MainActivity extends Activity {

    // These need to be relative to the root of external storage
    private static final String[] DEFAULT_PATHS = {
    };

    private static final String EXTRA_PATH = "path";
    private static final int REQUEST_PERMISSION = 1;

    private static Intent newIntent(Context context, String path) {
        return new Intent(context, MainActivity.class)
                .putExtra(EXTRA_PATH, path);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        applySystemUiVisibility(findViewById(R.id.list));

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
            files = Arrays.stream(DEFAULT_PATHS)
                    .flatMap(p -> getFiles(externalStorageDirectory, p))
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

    private static Stream<File> getFiles(File externalStorageDirectory, String path) {
        if (!path.endsWith("/*")) {
            return Stream.of(new File(externalStorageDirectory, path));
        }

        File dir = new File(externalStorageDirectory, path.substring(0, path.length() - "/*".length()));
        File[] files = dir.listFiles((d, name) -> !name.startsWith("."));
        return files != null ? Arrays.stream(files) : Stream.empty();
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

            context.startActivity(
                    new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, getMimeType(file.getName()))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            );
        }
    }

    private static String getMimeType(String name) {
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex < 0) {
            return "application/octet-stream";
        }

        String extension = name.substring(dotIndex + 1);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private void applySystemUiVisibility(View content) {
        getWindow().setDecorFitsSystemWindows(false);

        content.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(
                    systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom
            );

            return insets;
        });
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
