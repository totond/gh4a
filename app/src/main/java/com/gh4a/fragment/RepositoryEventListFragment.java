package com.gh4a.fragment;

import android.os.Bundle;

import com.gh4a.Gh4Application;
import com.meisolsson.githubsdk.model.GitHubEvent;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.service.activity.EventService;

import io.reactivex.Single;
import retrofit2.Response;

public class RepositoryEventListFragment extends EventListFragment {
    private Repository mRepository;

    public static RepositoryEventListFragment newInstance(Repository repository) {
        RepositoryEventListFragment f = new RepositoryEventListFragment();
        Bundle args = new Bundle();
        args.putParcelable("repository", repository);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRepository = getArguments().getParcelable("repository");
    }

    @Override
    protected Single<Response<Page<GitHubEvent>>> loadPage(int page) {
        final EventService service = Gh4Application.get().getGitHubService(EventService.class);
        return service.getRepositoryEvents(mRepository.owner().login(), mRepository.name(), page);
    }
}
