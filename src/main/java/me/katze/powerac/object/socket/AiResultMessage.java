package me.katze.powerac.object.socket;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public final class AiResultMessage {

    private String type;
    private String request_id;
    private String status;
    private Boolean detected;
    private Boolean limit_reached;
    private Double probability;
    private String reason;
    private String check_uuid;
    private List<AiResultModelMessage> results;

    public String getTypeOrEmpty() {
        return type == null ? "" : type;
    }

    public String getRequestIdOrEmpty() {
        return request_id == null ? "" : request_id;
    }

    public String getStatusOrDefault() {
        return status == null ? "error" : status;
    }

    public boolean isDetected() {
        return detected != null && detected;
    }

    public boolean isLimitReached() {
        return limit_reached != null && limit_reached;
    }

    public double getProbabilityOrUnknown() {
        return probability == null ? -1D : probability;
    }

    public String getReasonOrEmpty() {
        return reason == null ? "" : reason;
    }

    public String getCheckUuidOrEmpty() {
        return check_uuid == null ? "" : check_uuid;
    }

    public List<AiResultModelMessage> getResultsOrEmpty() {
        return results == null ? Collections.<AiResultModelMessage>emptyList() : results;
    }
    @Getter
    public static final class AiResultModelMessage {

        private Boolean global;
        private String status;
        private Boolean detected;
        private Boolean limit_reached;
        private Double probability;
        private String reason;
        private String check_uuid;

        public boolean isGlobalModel() {
            return global != null && global.booleanValue();
        }

        public String getStatusOrDefault() {
            return status == null ? "error" : status;
        }

        public boolean isDetected() {
            return detected != null && detected.booleanValue();
        }

        public boolean isLimitReached() {
            return limit_reached != null && limit_reached.booleanValue();
        }

        public double getProbabilityOrUnknown() {
            return probability == null ? -1D : probability.doubleValue();
        }

        public String getReasonOrEmpty() {
            return reason == null ? "" : reason;
        }

        public String getCheckUuidOrEmpty() {
            return check_uuid == null ? "" : check_uuid;
        }
    }
}
