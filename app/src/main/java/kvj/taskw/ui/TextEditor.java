package kvj.taskw.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import org.kvj.bravo7.form.FormController;
import org.kvj.bravo7.form.impl.ViewFinder;
import org.kvj.bravo7.form.impl.bundle.StringBundleAdapter;
import org.kvj.bravo7.form.impl.widget.TextViewCharSequenceAdapter;
import org.kvj.bravo7.form.impl.widget.TransientAdapter;
import org.kvj.bravo7.log.Logger;
import org.kvj.bravo7.util.Tasks;

import java.io.File;

import kvj.taskw.App;
import kvj.taskw.R;
import kvj.taskw.data.Controller;

/**
 * Created by vorobyev on 11/30/15.
 */
public class TextEditor extends AppCompatActivity {

    FormController form = new FormController(new ViewFinder.ActivityViewFinder(this));
    private Toolbar toolbar = null;
    Controller controller = App.controller();
    Logger logger = Logger.forInstance(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_editor);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        form.add(new TextViewCharSequenceAdapter(R.id.text_editor_input, null).trackOrigin(true), App.KEY_TEXT_INPUT);
        form.add(new TransientAdapter<>(new StringBundleAdapter(), null), App.KEY_TEXT_TARGET);
        form.load(this, savedInstanceState);
//        logger.d("On create:", form.getValue(App.KEY_TEXT_INPUT), form.getValue(App.KEY_TEXT_TARGET));
        if (null == form.getValue(App.KEY_TEXT_TARGET)) {
            // No data - load from Uri
            loadText(getIntent());
        } else {
            updateToolbar();
        }
    }

    private void updateToolbar() {
        toolbar.setSubtitle(form.getValue(App.KEY_TEXT_TARGET, String.class));
    }

    private void loadText(Intent intent) {
        // Load
        final File file = controller.fileFromIntentUri(intent);
        if (null == file) {
            // Invalid file
            controller.messageLong("Invalid file provided");
            finish();
            return;
        }
        new Tasks.ActivitySimpleTask<String>(this) {

            @Override
            protected String doInBackground() {
                return controller.readFile(file);
            }

            @Override
            public void finish(String result) {
                logger.d("File loaded:", file.getAbsolutePath(), result != null);
                if (null == result) {
                    controller.messageLong("File IO error");
                    TextEditor.this.finish();
                } else {
                    form.setValue(App.KEY_TEXT_TARGET, file.getAbsolutePath(), true);
                    form.setValue(App.KEY_TEXT_INPUT, result.trim(), true);
                    updateToolbar();
                }
            }
        }.exec();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        form.save(outState);
    }

    @Override
    public void onBackPressed() {
        if (!form.changed()) {
            super.onBackPressed(); // Close
            return;
        }
        logger.d("Changes:", form.changes());
        controller.question(this, "There are some changes, discard?", new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_text_editor, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_tb_save:
                save();
                break;
        }
        return true;
    }

    private void save() {
        if (!form.changed()) {
            finish(); //
        }
        final String fileName = form.getValue(App.KEY_TEXT_TARGET);
        final String text = form.getValue(App.KEY_TEXT_INPUT);
        new Tasks.ActivitySimpleTask<Boolean>(this) {

            @Override
            protected Boolean doInBackground() {
                String saveMe = text;
                if (!saveMe.isEmpty() && !saveMe.endsWith("\n")) { // Auto-add new line
                    saveMe += "\n";
                }
                return controller.saveFile(fileName, saveMe);
            }

            @Override
            public void finish(Boolean result) {
                if (!result) {
                    // Failure
                    controller.messageLong("File write failure");
                } else {
                    controller.messageShort("File saved");
                    setResult(RESULT_OK);
                    TextEditor.this.finish();
                }
            }
        }.exec();
    }

}
