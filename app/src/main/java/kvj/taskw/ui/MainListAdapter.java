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
    private Accessor<JSONObject, String> uuidAcc = new Accessor<JSONObject, String>() {
        @Override
        public String get(JSONObject object) {
            return object.optString("uuid");
        }
    };

    public interface ItemListener {
        public void onEdit(JSONObject json);
        public void onStatus(JSONObject json);
        public void onDelete(JSONObject json);
        public void onAnnotate(JSONObject json);
        public void onStartStop(JSONObject json);
        public void onDenotate(JSONObject json, JSONObject annJson);
        public void onCopyText(JSONObject json, String text);
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

    private void bindLongCopyText(final JSONObject json, View view, final String text) {
        if (TextUtils.isEmpty(text) || view == null || json == null) {
            return;
        }
        view.setOnLongClickListener(
            new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    logger.d("Long click on description", json);
                    if (null != listener) listener.onCopyText(json, text);
                    return true;
                }
            });
    }

    @Override
    public void onBindViewHolder(ListViewHolder holder, int position) {
        final JSONObject json = data.get(position);
        holder.card.removeAllViews();
        RemoteViews card = fill(holder.itemView.getContext(), json, info, urgMin, urgMax);
        holder.card.addView(card.apply(holder.itemView.getContext(), holder.card));
        final View bottomBtns = holder.card.findViewById(R.id.task_bottom_btns);
        final ViewGroup annotations = (ViewGroup) holder.card.findViewById(R.id.task_annotations);
        final View id = holder.card.findViewById(R.id.task_id);
        bottomBtns.setVisibility(View.GONE);
        holder.card.findViewById(R.id.task_more_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean visible = bottomBtns.getVisibility() == View.VISIBLE;
                int newVisibility = visible ? View.GONE : View.VISIBLE;
                bottomBtns.setVisibility(newVisibility);
                annotations.setVisibility(newVisibility);
                id.setVisibility(newVisibility);
            }
        });
        bindLongCopyText(json, holder.card.findViewById(R.id.task_description), json.optString("description"));
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
        holder.card.findViewById(R.id.task_start_stop_btn).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (null != listener)
                            listener.onStartStop(json);
                    }
                });
        JSONArray annotationsArr = json.optJSONArray("annotations");
        if (null != annotationsArr && annotationsArr.length() == annotations.getChildCount()) {
            // Bind delete button
            for (int i = 0; i < annotationsArr.length(); i++) { // Show and bind delete button
                JSONObject jsonAnn = annotationsArr.optJSONObject(i);
                bindLongCopyText(json,
                                 annotations.getChildAt(i).findViewById(R.id.task_ann_text),
                                 jsonAnn.optString("description"));
                View deleteBtn = annotations.getChildAt(i).findViewById(R.id.task_ann_delete_btn);
                if (null != deleteBtn) {
                    deleteBtn.setVisibility(View.VISIBLE);
                    deleteBtn.setOnClickListener(denotate(json, jsonAnn));
                }
            }
        }
    }

    private View.OnClickListener denotate(final JSONObject json, final JSONObject annJson) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != listener) {
                    listener.onDenotate(json, annJson);
                }
            }
        };
    }

    interface Accessor<O, V> {
        V get(O object);
    }

    private static <O, V> int indexOf(Collection<O> from, Accessor<O, V> acc, V value) {
        int index = 0;
        for (O item : from) { // $COMMENT
            if (value.equals(acc.get(item))) { // Found
                return index;
            }
            index++;
        }
        return -1;
    }

    public <O, V> void morph(List<O> from, List<O> to, Accessor<O, V> acc) {
        for (int i = 0; i < to.size();) {
            O item = to.get(i);
            V id = acc.get(item);
            boolean remove = indexOf(from, acc, id) == -1; // Item not found in new array
            if (remove) { //
                notifyItemRemoved(i);
                to.remove(i);
            } else {
                i++;
            }
        }
        for (int i = 0; i < from.size(); i++) {
            O item = from.get(i);
            V id = acc.get(item);
            int idx =  indexOf(to, acc, id); // Location in old array
            if (idx == -1) { // Add item
                notifyItemInserted(i);
                to.add(i, item);
            } else {
                notifyItemMoved(idx, i);
                to.remove(idx);
                to.add(i, item);
                notifyItemChanged(i);
            }
        }
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
        morph(list, data, uuidAcc);
//        data.clear();
//        data.addAll(list);
//        notifyDataSetChanged();
    }

    public static class ListViewHolder extends RecyclerView.ViewHolder {

        private final CardView card;

        public ListViewHolder(View itemView) {
            super(itemView);
            card = (CardView) itemView.findViewById(R.id.card_card);
        }
    }

    public static RemoteViews fill(Context context, JSONObject json, ReportInfo info, int urgMin, int urgMax) {
//        logger.d("Fill", json, info.fields);
        String status = json.optString("status", "pending");
        boolean pending = "pending".equalsIgnoreCase(status);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.item_one_task);
        views.setViewVisibility(R.id.task_urgency, info.fields.containsKey("urgency")? View.VISIBLE: View.GONE);
        views.setViewVisibility(R.id.task_priority, info.fields.containsKey("priority") ? View.VISIBLE : View.GONE);
        views.setImageViewResource(R.id.task_status_btn, status2icon(status));
        views.setViewVisibility(R.id.task_annotations, View.GONE);
        views.setViewVisibility(R.id.task_annotations_flag, View.GONE);
        views.setViewVisibility(R.id.task_start_stop_btn, View.GONE);
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
            if (field.getKey().equalsIgnoreCase("id")) {
                views.setTextViewText(R.id.task_id, String.format("[%d]", json.optInt("id", -1)));
            }
            if (field.getKey().equalsIgnoreCase("priority")) {
                int index = info.priorities.indexOf(json.optString("priority", ""));
                if(index == -1) {
                    views.setProgressBar(R.id.task_priority, 0, 0, false);
                } else {
                    views.setProgressBar(R.id.task_priority, info.priorities.size() - 1,
                                         info.priorities.size() - index - 1, false);
                }
            }
            if (field.getKey().equalsIgnoreCase("urgency")) {
                views.setProgressBar(R.id.task_urgency, urgMax-urgMin, (int)Math.round(json.optDouble("urgency"))-urgMin, false);
            }
            if (field.getKey().equalsIgnoreCase("due")) {
                addLabel(context, views, true, R.drawable.ic_label_due,
                         asDate(json.optString("due"), field.getValue(), formattedFormat));
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
                addLabel(context, views, false, R.drawable.ic_label_tags, join(", ", array2List(
                    json.optJSONArray("tags"))));
            }
            if (field.getKey().equalsIgnoreCase("start")) {
                String started = asDate(json.optString("start"), field.getValue(), formattedFormatDT);
                boolean isStarted = !TextUtils.isEmpty(started);
                if (pending) { // Can be started/stopped
                    views.setViewVisibility(R.id.task_start_stop_btn, View.VISIBLE);
                    views.setImageViewResource(R.id.task_start_stop_btn, isStarted? R.drawable.ic_action_stop: R.drawable.ic_action_start);
                }
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
