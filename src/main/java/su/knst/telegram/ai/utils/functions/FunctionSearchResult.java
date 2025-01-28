package su.knst.telegram.ai.utils.functions;

import su.knst.telegram.ai.utils.functions.search.SearchInfo;

import java.util.List;

public class FunctionSearchResult extends FunctionResult {
    public List<SearchInfo> searchInfos;

    public FunctionSearchResult(List<SearchInfo> searchInfos) {
        super(true);

        this.searchInfos = searchInfos;
    }
}
