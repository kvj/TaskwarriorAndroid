package kvj.taskw;


import android.accounts.Account;

import kvj.taskw.data.Controller;

/**
 * Created by vorobyev on 10/4/15.
 */
public class App extends org.kvj.bravo7.ng.App<Controller> {

    public static final String ACCOUNT_TYPE = "kvj.task.account";
    public static final String KEY_ACCOUNT = "account";
    public static final String KEY_REPORT = "report";
    public static final String KEY_QUERY = "query";
    public static final String KEY_EDIT_UUID = "editor_uuid";
    public static final String KEY_EDIT_DESCRIPTION = "editor_description";
    public static final String KEY_EDIT_PROJECT = "editor_project";
    public static final String KEY_EDIT_TAGS = "editor_tags";
    public static final String KEY_EDIT_DUE = "editor_due";
    public static final String KEY_EDIT_WAIT = "editor_wait";
    public static final String KEY_EDIT_SCHEDULED = "editor_scheduled";
    public static final String KEY_EDIT_RECUR = "editor_recur";
    public static final String KEY_EDIT_UNTIL = "editor_until";
    public static final String KEY_EDIT_PRIORITY = "editor_priority";
    public static final int EDIT_REQUEST = 1;
    public static final int SYNC_REQUEST = 2;
    public static final String KEY_EDIT_TEXT = "editor_text";
    public static final int ANNOTATE_REQUEST = 3;
    public static final int SETTINGS_REQUEST = 4;
    public static final String KEY_EDIT_STATUS = "editor_status";
    public static final String ACCOUNT_FOLDER = "folder";
    public static final String KEY_TEXT_INPUT = "text_editor_input";
    public static final String KEY_TEXT_TARGET = "text_editor_target";
    public static final String KEY_QUERY_INPUT = "query_input";
    public static final String KEY_RUN_COMMAND = "run_command";
    public static final String KEY_RUN_OUTPUT = "run_output";
    public static final String KEY_EDIT_DATA = "editor_data";
    public static final String KEY_EDIT_DATA_FIELDS = "editor_data_fields";
    public static final String[] BUILTIN_REPORTS = {
            "burndown.daily",
            "burndown.monthly",
            "burndown.weekly",
            "calendar",
            "colors",
            "export",
            "ghistory.annual",
            "ghistory.monthly",
            "history.annual",
            "history.monthly",
            "information",
            "summary",
            "timesheet",
            "projects"};
    public static final String LOG_FILE = "taskw.log.txt";

    @Override
    protected Controller create() {
        return new Controller(this, "Taskwarrior");
    }

    @Override
    protected void init() {
        Controller controller = App.controller();
        for (Account acc : controller.accounts()) {
            controller.accountController(controller.accountID(acc)); // This will schedule sync
        }
    }
}
