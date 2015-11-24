package kvj.taskw.ui;

import android.content.Context;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;
import org.kvj.bravo7.log.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import kvj.taskw.R;
import kvj.taskw.data.ReportInfo;

/**
 * Created by vorobyev on 11/19/15.
 */
public class MainListAdapter extends RecyclerView.Adapter<MainListAdapter.ListViewHolder> {

    private int urgMin;
    private int urgMax;

    public interface ItemListener {
        public void onEdit(JSONObject json);
        public void onStatus(JSONObject json);
        public void onDelete(JSONObject json);
        public void onAnnotate(JSONObject json);
        public void onStartStop(JSONObject json);
    }

    List<JSONObject> data = new ArrayList<>();
    static Logger logger = Logger.forClass(MainListAdapter.class);
    private ReportInfo info = null;
    private ItemListener listener = null;

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
        final JSONObject json = data.get(position);
        holder.card.removeAllViews();
        RemoteViews card = fill(holder.itemView.getContext(), json, info, urgMin, urgMax);
        holder.card.addView(card.apply(holder.itemView.getContext(), holder.card));
        final View bottomBtns = holder.card.findViewById(R.id.task_bottom_btns);
        final View annotations = holder.card.findViewById(R.id.task_annotations);
        bottomBtns.setVisibility(View.GONE);
        holder.card.findViewById(R.id.task_more_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean visible = bottomBtns.getVisibility() == View.VISIBLE;
                bottomBtns.setVisibility(visible ? View.GONE : View.VISIBLE);
                annotations.setVisibility(visible ? View.GONE : View.VISIBLE);
            }
        });
        holder.card.findViewById(R.id.task_edit_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != listener)
                    listener.onEdit(json);
            }
        });
        holder.card.findViewById(R.id.task_status_btn).setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != listener)
                        listener.onStatus(json);
                }
            });
        holder.card.findViewById(R.id.task_delete_btn).setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != listener)
                        listener.onDelete(json);
                }
            });
        holder.card.findViewById(R.id.task_annotate_btn).setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != listener)
                        listener.onAnnotate(json);
                }
            });
    }

    public void update(List<JSONObject> list, ReportInfo info) {
        this.info = info;
        boolean hasUrgency = info.fields.containsKey("urgency");
        if (hasUrgency && !list.isEmpty()) { // Search
            double min = list.get(0).optDouble("urgency");
            double max = min;
            for (JSONObject json : list) { // Find min and max
                double urg = json.optDouble("urgency");
                if (min > urg) {
                    min = urg;
                }
                if (max < urg) {
                    max = urg;
                }
            }
            urgMin = (int) Math.floor(min);
            urgMax = (int) Math.ceil(max);
        }
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

    public static RemoteViews fill(Context context, JSONObject json, ReportInfo info, int urgMin, int urgMax) {
        logger.d("Fill", json, info.fields);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.item_one_task);
        views.setViewVisibility(R.id.task_urgency, info.fields.containsKey("urgency")? View.VISIBLE: View.GONE);
        views.setViewVisibility(R.id.task_priority, info.fields.containsKey("priority")? View.VISIBLE: View.GONE);
        views.setImageViewResource(R.id.task_status_btn, status2icon(json.optString("status")));
        views.setViewVisibility(R.id.task_annotations, View.GONE);
        views.setViewVisibility(R.id.task_annotations_flag, View.GONE);
        for (Map.Entry<String, String> field : info.fields.entrySet()) {
            if (field.getKey().equalsIgnoreCase("description")) {
                // Show title
                views.setTextViewText(R.id.task_description, json.optString("description"));
                JSONArray annotations = json.optJSONArray("annotations");
                if (null != annotations && annotations.length() > 0) {
                    // Have annotations
                    views.setViewVisibility(R.id.task_annotations_flag, View.VISIBLE);
                    if ("".equals(field.getValue())) {
                        for (int i = 0; i < annotations.length(); i++) {
                            JSONObject ann = annotations.optJSONObject(i);
                            RemoteViews annView = new RemoteViews(context.getPackageName(), R.layout.item_one_annotation);
                            annView.setTextViewText(R.id.task_ann_text, ann.optString("description", "Untitled"));
                            annView.setTextViewText(R.id.task_ann_date, asDate(ann.optString("entry"), "", formattedFormatDT));
                            views.addView(R.id.task_annotations, annView);
                        }
                    }
                }
            }
            if (field.getKey().equalsIgnoreCase("priority")) {
                int index = info.priorities.indexOf(json.optString("priority", ""));
                if(index == -1) {
                    views.setProgressBar(R.id.task_priority, 0, 0, false);
                } else {
                    views.setProgressBar(R.id.task_priority, info.priorities.size()-1, info.priorities.size()-index-1, false);
                }
            }
            if (field.getKey().equalsIgnoreCase("urgency")) {
                views.setProgressBar(R.id.task_urgency, urgMax-urgMin, (int)Math.round(json.optDouble("urgency"))-urgMin, false);
            }
            if (field.getKey().equalsIgnoreCase("due")) {
                addLabel(context, views, true, R.drawable.ic_label_due, asDate(json.optString("due"), field.getValue(), formattedFormat));
            }
            if (field.getKey().equalsIgnoreCase("wait")) {
                addLabel(context, views, true, R.drawable.ic_label_wait, asDate(json.optString("wait"), field.getValue(), formattedFormat));
            }
            if (field.getKey().equalsIgnoreCase("scheduled")) {
                addLabel(context, views, true, R.drawable.ic_label_scheduled, asDate(json.optString("scheduled"), field.getValue(), formattedFormat));
            }
            if (field.getKey().equalsIgnoreCase("recur")) {
                String recur = json.optString("recur");
                if (!TextUtils.isEmpty(recur) && info.fields.containsKey("until")) {
                    String until = asDate(json.optString("until"), info.fields.get("until"), formattedFormat);
                    if (!TextUtils.isEmpty(until)) {
                        recur += String.format(" ~ %s", until);
                    }
                }
                addLabel(context, views, true, R.drawable.ic_label_recur, recur);
            }
            if (field.getKey().equalsIgnoreCase("project")) {
                addLabel(context, views, false, R.drawable.ic_label_project, json.optString("project"));
            }
            if (field.getKey().equalsIgnoreCase("tags")) {
                addLabel(context, views, false, R.drawable.ic_label_tags, join(", ", array2List(json.optJSONArray("tags"))));
            }
        }
        return views;
    }

    public static String join(String with, Iterable<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String item : list) { // Join
            if (TextUtils.isEmpty(item)) { // Skip all empty
                continue;
            }
            if (sb.length() > 0) { // Not empty
                sb.append(with);
            }
            sb.append(item);
        }
        return sb.toString();
    }

    public static Collection<String> array2List(JSONArray arr) {
        List<String> result = new ArrayList<>();
        if (null == arr) {
            return result;
        }
        for (int i = 0; i < arr.length(); i++) { // $COMMENT
            String value = arr.optString(i);
            if (!TextUtils.isEmpty(value)) { // Only non-empty
                result.add(value);
            }
        }
        return result;
    }

    public static DateFormat formattedFormat = new SimpleDateFormat("yyyy-MM-dd");
    public static DateFormat formattedFormatDT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public static String asDate(String due, String value, DateFormat format) {
        DateFormat jsonFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        jsonFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        if (TextUtils.isEmpty(due)) { // No value
            return null;
        }
        try {
            Date parsed = jsonFormat.parse(due);
            logger.d("Parsed", parsed, due);
            return format.format(parsed);
        } catch (Exception e) {
            logger.e(e, "Failed to parse Date:", due);
        }
        return null;
    }

    private static int status2icon(String status) {
        if ("deleted".equalsIgnoreCase(status)) return R.drawable.ic_status_deleted;
        if ("completed".equalsIgnoreCase(status)) return R.drawable.ic_status_completed;
        if ("waiting".equalsIgnoreCase(status)) return R.drawable.ic_status_waiting;
        if ("recurring".equalsIgnoreCase(status)) return R.drawable.ic_status_recurring;
        return R.drawable.ic_status_pending;
    }

    private static void addLabel(Context context, RemoteViews views, boolean left, int icon, String text) {
        if (TextUtils.isEmpty(text)) { // No label
            return;
        }
        RemoteViews line = new RemoteViews(context.getPackageName(), left? R.layout.item_one_label_left: R.layout.item_one_label_right);
        line.setTextViewText(R.id.label_text, text);
        line.setImageViewResource(R.id.label_icon, icon);
        views.addView(left? R.id.task_labels_left: R.id.task_labels_right, line);
    }

    public void listener(ItemListener listener) {
        this.listener = listener;
    }
}
