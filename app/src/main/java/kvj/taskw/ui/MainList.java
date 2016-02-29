package kvj.taskw.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONObject;
import org.kvj.bravo7.form.FormController;
import org.kvj.bravo7.log.Logger;
import org.kvj.bravo7.util.Tasks;

import java.util.List;

import kvj.taskw.App;
import kvj.taskw.R;
import kvj.taskw.data.Controller;
import kvj.taskw.data.ReportInfo;

/**
 * Created by vorobyev on 11/19/15.
 */
public class MainList extends Fragment {

    private RecyclerView list = null;
    private ReportInfo info = null;
    Controller controller = App.controller();
    Logger logger = Logger.forInstance(this);
    private MainListAdapter adapter = null;
    private String account = null;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        list = (RecyclerView) view.findViewById(R.id.list_main_list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MainListAdapter(getResources());
        list.setAdapter(adapter);
        return view;
    }

    public void load(final FormController form, final Runnable afterLoad) {
        this.account = form.getValue(App.KEY_ACCOUNT);
        final String report = form.getValue(App.KEY_REPORT);
        final String query = form.getValue(App.KEY_QUERY);
        new Tasks.ActivitySimpleTask<ReportInfo>(getActivity()){

            @Override
            protected ReportInfo doInBackground() {
                logger.d("Load:", query, report);
                return controller.accountController(account).taskReportInfo(report, query);
            }

            @Override
            public void finish(ReportInfo result) {
                info = result;
                if (null != afterLoad) afterLoad.run();
                reload();
            }
        }.exec();
    }

    public void reload() {
        if (null == info || null == account) return;
        // Load all items
        new Tasks.ActivitySimpleTask<List<JSONObject>>(getActivity()){

            @Override
            protected List<JSONObject> doInBackground() {
                logger.d("Exec:", info.query);
                List<JSONObject> list = controller.accountController(account).taskList(info.query);
                info.sort(list); // Sorted according to report spec.
                return list;
            }

            @Override
            public void finish(List<JSONObject> result) {
                adapter.update(result, info);
//                logger.d("Loaded:", info, result);
            }
        }.exec();

    }

    public void listener(MainListAdapter.ItemListener listener) {
        adapter.listener(listener);
    }


    public ReportInfo reportInfo() {
        return info;
    }
}
