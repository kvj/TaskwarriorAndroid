package kvj.taskw.ui;

import android.content.Context;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;

import org.json.JSONObject;
import org.kvj.bravo7.log.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kvj.taskw.R;
import kvj.taskw.data.ReportInfo;

/**
 * Created by vorobyev on 11/19/15.
 */
public class MainListAdapter extends RecyclerView.Adapter<MainListAdapter.ListViewHolder> {

    List<JSONObject> data = new ArrayList<>();
    Logger logger = Logger.forInstance(this);
    private ReportInfo info = null;

    @Override
    public ListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ListViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_one_card, parent, false));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public void onBindViewHolder(ListViewHolder holder, int position) {
        JSONObject json = data.get(position);
        holder.card.removeAllViews();
        RemoteViews card = fill(holder.itemView.getContext(), json, info);
        holder.card.addView(card.apply(holder.itemView.getContext(), holder.card));;
    }

    public void update(List<JSONObject> list, ReportInfo info) {
        this.info = info;
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    public static class ListViewHolder extends RecyclerView.ViewHolder {

        private final CardView card;

        public ListViewHolder(View itemView) {
            super(itemView);
            card = (CardView) itemView.findViewById(R.id.card_card);
        }
    }

    public static RemoteViews fill(Context context, JSONObject json, ReportInfo info) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.item_one_task);
        views.setImageViewResource(R.id.task_status_btn, status2icon(json.optString("status")));
        for (Map.Entry<String, String> field : info.fields.entrySet()) {
            if (field.getKey().equalsIgnoreCase("description")) {
                // Show title
                views.setTextViewText(R.id.task_description, json.optString("description"));
            }
        }
        return views;
    }

    private static int status2icon(String status) {
        if ("deleted".equalsIgnoreCase(status)) return R.drawable.ic_status_deleted;
        if ("completed".equalsIgnoreCase(status)) return R.drawable.ic_status_completed;
        if ("waiting".equalsIgnoreCase(status)) return R.drawable.ic_status_waiting;
        if ("recurring".equalsIgnoreCase(status)) return R.drawable.ic_status_recurring;
        return R.drawable.ic_status_pending;
    }
}
