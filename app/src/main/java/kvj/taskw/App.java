package kvj.taskw;


/**
 * Created by vorobyev on 10/4/15.
 */
public class App extends org.kvj.bravo7.ng.App<Controller> {

    public static final String ACCOUNT_TYPE = "kvj.task.account";

    @Override
    protected Controller create() {
        return new Controller(this, "Taskwarrior");
    }
}
