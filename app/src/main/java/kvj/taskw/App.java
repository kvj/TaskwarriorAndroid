package kvj.taskw;


/**
 * Created by vorobyev on 10/4/15.
 */
public class App extends org.kvj.bravo7.ng.App<Controller> {

    @Override
    protected Controller create() {
        return new Controller(this, "Taskwarrior");
    }
}
