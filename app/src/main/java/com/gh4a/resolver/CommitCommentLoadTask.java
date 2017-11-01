package com.gh4a.resolver;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.FragmentActivity;

import com.gh4a.ApiRequestException;
import com.gh4a.Gh4Application;
import com.gh4a.activities.CommitActivity;
import com.gh4a.activities.CommitDiffViewerActivity;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.FileUtils;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.RxUtils;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommentService;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommitService;

import java.util.List;

import io.reactivex.Single;

public class CommitCommentLoadTask extends UrlLoadTask {
    @VisibleForTesting
    protected final String mRepoOwner;
    @VisibleForTesting
    protected final String mRepoName;
    @VisibleForTesting
    protected final String mCommitSha;
    @VisibleForTesting
    protected final IntentUtils.InitialCommentMarker mMarker;

    public CommitCommentLoadTask(FragmentActivity activity, String repoOwner, String repoName,
            String commitSha, IntentUtils.InitialCommentMarker marker,
            boolean finishCurrentActivity) {
        super(activity, finishCurrentActivity);
        mRepoOwner = repoOwner;
        mRepoName = repoName;
        mCommitSha = commitSha;
        mMarker = marker;
    }

    @Override
    protected Intent run() throws ApiRequestException {
        return load(mActivity, mRepoOwner, mRepoName, mCommitSha, mMarker).blockingGet();
    }

    public static Single<Intent> load(Context context,
            String repoOwner, String repoName, String commitSha,
            IntentUtils.InitialCommentMarker marker) {
        RepositoryCommitService commitService =
                Gh4Application.get().getGitHubService(RepositoryCommitService.class);
        RepositoryCommentService commentService =
                Gh4Application.get().getGitHubService(RepositoryCommentService.class);

        Single<Commit> commitSingle = commitService.getCommit(repoOwner, repoName, commitSha)
                .map(ApiHelpers::throwOnFailure);
        Single<List<GitComment>> commentSingle = ApiHelpers.PageIterator
                .toSingle(page -> commentService.getCommitComments(repoOwner, repoName, commitSha, page));

        Single<GitHubFile> fileSingle = commentSingle
                .compose(RxUtils.filter(c -> marker.matches(c.id(), c.createdAt())))
                .map(list -> list.isEmpty() ? null : list.get(0))
                .zipWith(commitSingle, (comment, commit) -> {
                    if (comment != null) {
                        for (GitHubFile commitFile : commit.files()) {
                            if (commitFile.filename().equals(comment.path())) {
                                return commitFile;
                            }
                        }
                    }
                    return null;
                });

        return Single.zip(commitSingle, commentSingle, fileSingle, (commit, comments, file) -> {
            if (file != null && !FileUtils.isImage(file.filename())) {
                return CommitDiffViewerActivity.makeIntent(context, repoOwner, repoName,
                        commitSha, file.filename(), file.patch(),
                        comments, -1, -1, false, marker);
            } else if (file == null) {
                return CommitActivity.makeIntent(context, repoOwner, repoName, commitSha, marker);
            }
            return null;
        });
    }
}
