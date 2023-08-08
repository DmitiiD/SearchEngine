package searchengine.dto.statistics;

import lombok.Data;

@Data
public class IndexingResponse {
    private boolean result;
    private String error; //empty if without troubles
}
