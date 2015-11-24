package kvj.taskw.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;

import org.kvj.bravo7.form.FormController;
import org.kvj.bravo7.form.impl.widget.SpinnerIntegerAdapter;
import org.kvj.bravo7.form.impl.widget.TextViewCharSequenceAdapter;
import org.kvj.bravo7.log.Logger;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import kvj.taskw.App;
import kvj.taskw.R;

/**
 * Created by kvorobyev on 11/21/15.
 */
public class Editor extends Fragment {

    Logger logger = Logger.forInstance(this);
    private Spinner prioritiesSpinner = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_editor, container, false);
        setupDatePicker(view, R.id.editor_due, R.id.editor_due_btn);
        setupDatePicker(view, R.id.editor_wait, R.id.editor_wait_btn);
        setupDatePicker(view, R.id.editor_scheduled, R.id.editor_scheduled_btn);
        setupDatePicker(view, R.id.editor_until, R.id.editor_until_btn);
        prioritiesSpinner = (Spinner) view.findViewById(R.id.editor_priority);
        return view;
    }

    public void initForm(FormController form) {
        form.add(new TextViewCharSequenceAdapter(R.id.editor_description, ""), App.KEY_EDIT_DESCRIPTION);
        form.add(new TextViewCharSequenceAdapter(R.id.editor_project, ""), App.KEY_EDIT_PROJECT);
        form.add(new TextViewCharSequenceAdapter(R.id.editor_tags, ""), App.KEY_EDIT_TAGS);
        form.add(new TextViewCharSequenceAdapter(R.id.editor_due, ""), App.KEY_EDIT_DUE);
        form.add(new TextViewCharSequenceAdapter(R.id.editor_wait, ""), App.KEY_EDIT_WAIT);
        form.add(new TextViewCharSequenceAdapter(R.id.editor_scheduled, ""), App.KEY_EDIT_SCHEDULED);
        form.add(new TextViewCharSequenceAdapter(R.id.editor_until, ""), App.KEY_EDIT_UNTIL);
        form.add(new TextViewCharSequenceAdapter(R.id.editor_recur, ""), App.KEY_EDIT_RECUR);
        form.add(new SpinnerIntegerAdapter(R.id.editor_priority, -1), App.KEY_EDIT_PRIORITY);
    }



    public static Date fromInput(String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        try {
            return MainListAdapter.formattedFormat.parse(text);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void setupDatePicker(View view, int text, int btn) {
        final EditText textInput = (EditText) view.findViewById(text);
        view.findViewById(btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Date dt = fromInput(textInput.getText().toString().trim());
                if (null == dt) { // Invalid input
                    dt = new Date();
                }
                final Calendar c = Calendar.getInstance();
                c.setTime(dt);
                DatePickerDialog dialog = new DatePickerDialog(getContext(), new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        logger.d("Date set:", year, monthOfYear, dayOfMonth);
                        c.set(Calendar.DAY_OF_MONTH, 1);
                        c.set(Calendar.YEAR, year);
                        c.set(Calendar.MONTH, monthOfYear);
                        c.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        textInput.setText(MainListAdapter.formattedFormat.format(c.getTime()));
                    }
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
                dialog.show();
            }
        });
    }

    public void setupPriorities(List<String> strings) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_item, strings);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        prioritiesSpinner.setAdapter(adapter);
    }
}
