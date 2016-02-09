package kvj.taskw.data;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by vorobyev on 11/19/15.
 */
public class ReportInfo {

    public Map<String, Boolean> sort = new LinkedHashMap<>();
    public Map<String, String> fields = new LinkedHashMap<>();
    public String query = "";
    public String description = "Untitled";
    public List<String> priorities = new ArrayList<>();

    @Override
    public String toString() {
        return String.format("ReportInfo: %s [%s %s] %s", query, fields.toString(), sort.toString(), description);
    }

    public void sort(List<JSONObject> list) {
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject lhs, JSONObject rhs) {
                for (Map.Entry<String, Boolean> entry : sort.entrySet()) {
                    Object lo = lhs.opt(entry.getKey());
                    Object ro = rhs.opt(entry.getKey());
                    if (lo == null && ro != null) {
                        return 1;
                    }
                    if (lo != null && ro == null) {
                        return -1;
                    }
                    if (lo == null && ro == null) {
                        continue;
                    }
                    if (lo instanceof Number) {
                        double ld = ((Number)lo).doubleValue();
                        double rd = ((Number)ro).doubleValue();
                        if (ld == rd)
                            continue;
                        return (ld > rd? 1: -1) * (entry.getValue()? 1: -1);
                    }
                    if (lo instanceof String) {
                        int result = ((String)lo).compareTo((String) ro);
                        if (result == 0)
                            continue;
                        return result * (entry.getValue()? 1: -1);
                    }
                }
                return 0;
            }
        });
    }
}
