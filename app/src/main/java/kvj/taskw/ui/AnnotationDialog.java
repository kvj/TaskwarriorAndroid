package kvj.taskw.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;

import org.kvj.bravo7.form.FormController;
import org.kvj.bravo7.form.impl.ViewFinder;
import org.kvj.bravo7.form.impl.bundle.StringBundleAdapter;
import org.kvj.bravo7.form.impl.widget.TextViewCharSequenceAdapter;
import org.kvj.bravo7.form.impl.widget.TransientAdapter;
import org.kvj.bravo7.util.Tasks;

import kvj.taskw.App;
import kvj.taskw.R;
import kvj.taskw.data.AccountController;
import kvj.taskw.data.Controller;

/**
 * Created by kvorobyev on 11/25/15.
 */
public class AnnotationDialog extends AppCompatActivity {
    Controller controller = App.controller();

    FormController form = new FormController(new ViewFinder.ActivityViewFinder(this));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_add_annotation);
        form.add(new TransientAdapter<>(new StringBundleAdapter(), null), App.KEY_ACCOUNT);
        form.add(new TransientAdapter<>(new StringBundleAdapter(), null), App.KEY_EDIT_UUID);
        form.add(new TextViewCharSequenceAdapter(R.id.ann_text, ""), App.KEY_EDIT_TEXT);
        form.load(this, savedInstanceState);
        findViewById(R.id.ann_cancel_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doFinish();
            }
        });
        findViewById(R.id.ann_ok_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSave();
            }
        });
    }

    private void doSave() {
        final String text = form.getValue(App.KEY_EDIT_TEXT);
        if (TextUtils.isEmpty(text)) { // Nothing to save
            controller.messageShort("Input is mandatory");
            return;
        }
        final AccountController ac = controller.accountController(form.getValue(App.KEY_ACCOUNT, String.class));
        new Tasks.ActivitySimpleTask<String>(this){

            @Override
            protected String doInBackground() {
                String uuid = form.getValue(App.KEY_EDIT_UUID);
                return ac.taskAnnotate(uuid, text);
            }

            @Override
            public void finish(String result) {
                if (null != result) { // Error
                    controller.messageShort(result);
                } else {
                    setResult(RESULT_OK);
                    AnnotationDialog.this.finish();
                }
            }
        }.exec();
    }

    private void doFinish() {
        if (form.changed()) { // Ask for confirmation
            controller.question(AnnotationDialog.this, "There are some changes, discard?", new Runnable() {

                @Override
                public void run() {
                    finish();
                }
            }, null);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        doFinish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        form.save(outState);
    }
}
