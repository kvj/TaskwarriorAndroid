package kvj.taskw;


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

    @Override
    protected Controller create() {
        return new Controller(this, "Taskwarrior");
    }

    @Override
    protected void init() {
        Controller controller = App.controller();
        for (String name : controller.accounts()) {
            controller.accountController(name); // This will schedule sync
        }
    }
}
