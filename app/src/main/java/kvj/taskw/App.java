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

    @Override
    protected Controller create() {
        return new Controller(this, "Taskwarrior");
    }
}
