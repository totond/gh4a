package com.gh4a.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.activities.EditIssueCommentActivity;
import com.gh4a.activities.EditPullRequestCommentActivity;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.adapter.timeline.TimelineItemAdapter;
import com.gh4a.model.TimelineItem;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.RxUtils;
import com.meisolsson.githubsdk.model.GitHubCommentBase;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.request.ReactionRequest;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestService;
import com.meisolsson.githubsdk.service.reactions.ReactionService;
import com.meisolsson.githubsdk.service.issues.IssueCommentService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewCommentService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.reactivex.Single;
import retrofit2.Response;

public class ReviewFragment extends ListDataBaseFragment<TimelineItem>
        implements TimelineItemAdapter.OnCommentAction {

    private static final int REQUEST_EDIT = 1000;

    @Nullable
    private TimelineItemAdapter mAdapter;

    public static ReviewFragment newInstance(String repoOwner, String repoName, int issueNumber,
            Review review, IntentUtils.InitialCommentMarker mInitialComment) {
        ReviewFragment f = new ReviewFragment();
        Bundle args = new Bundle();
        args.putString("repo_owner", repoOwner);
        args.putString("repo_name", repoName);
        args.putInt("issue_number", issueNumber);
        args.putParcelable("review", review);
        args.putParcelable("initial_comment", mInitialComment);
        f.setArguments(args);
        return f;
    }

    private String mRepoOwner;
    private String mRepoName;
    private int mIssueNumber;
    private Review mReview;
    private IntentUtils.InitialCommentMarker mInitialComment;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        mRepoOwner = args.getString("repo_owner");
        mRepoName = args.getString("repo_name");
        mIssueNumber = args.getInt("issue_number");
        mReview = args.getParcelable("review");
        mInitialComment = args.getParcelable("initial_comment");
        args.remove("initial_comment");
    }

    @Override
    protected Single<List<TimelineItem>> onCreateDataSingle() {
        final Gh4Application app = Gh4Application.get();
        final PullRequestService prService = app.getGitHubService(PullRequestService.class);
        final PullRequestReviewService reviewService =
                app.getGitHubService(PullRequestReviewService.class);
        final PullRequestReviewCommentService commentService =
                app.getGitHubService(PullRequestReviewCommentService.class);

        Single<TimelineItem.TimelineReview> reviewItemSingle =
                reviewService.getReview(mRepoOwner, mRepoName, mIssueNumber, mReview.id())
                .map(ApiHelpers::throwOnFailure)
                .map(review -> new TimelineItem.TimelineReview(review));

        Single<List<ReviewComment>> reviewCommentsSingle = ApiHelpers.PageIterator
                .toSingle(page -> reviewService.getReviewComments(
                        mRepoOwner, mRepoName, mIssueNumber, mReview.id()))
                .compose(RxUtils.sortList(ApiHelpers.COMMENT_COMPARATOR))
                .cache(); // single is used multiple times -> avoid refetching data

        Single<Boolean> hasCommentsSingle = reviewCommentsSingle
                .map(comments -> !comments.isEmpty());

        Single<List<GitHubFile>> filesSingle = hasCommentsSingle
                .flatMap(hasComments -> {
                    if (!hasComments) {
                        return Single.just(null);
                    }
                    return ApiHelpers.PageIterator
                            .toSingle(page -> prService.getPullRequestFiles(
                                    mRepoOwner, mRepoName, mIssueNumber, page));
                });

        Single<List<ReviewComment>> commentsSingle = hasCommentsSingle
                .flatMap(hasComments -> {
                    if (!hasComments) {
                        return Single.just(null);
                    }
                    return ApiHelpers.PageIterator
                            .toSingle(page -> commentService.getPullRequestComments(
                                    mRepoOwner, mRepoName, mIssueNumber, page))
                            .compose(RxUtils.sortList(ApiHelpers.COMMENT_COMPARATOR));
                });

        return Single.zip(reviewItemSingle, reviewCommentsSingle, filesSingle, commentsSingle,
                (reviewItem, reviewComments, files, comments) -> {
            if (!reviewComments.isEmpty()) {
                HashMap<String, GitHubFile> filesByName = new HashMap<>();
                for (GitHubFile file : files) {
                    filesByName.put(file.filename(), file);
                }

                // Add all of the review comments to the review item creating necessary diff hunks
                for (ReviewComment reviewComment : reviewComments) {
                    GitHubFile file = filesByName.get(reviewComment.path());
                    reviewItem.addComment(reviewComment, file, true);
                }

                for (ReviewComment commitComment : comments) {
                    if (reviewComments.contains(commitComment)) {
                        continue;
                    }

                    // Rest of the comments should be added only if they are under the same diff hunks
                    // as the original review comments.
                    GitHubFile file = filesByName.get(commitComment.path());
                    reviewItem.addComment(commitComment, file, false);
                }
            }

            List<TimelineItem> items = new ArrayList<>();
            items.add(reviewItem);

            List<TimelineItem.Diff> diffHunks = new ArrayList<>(reviewItem.getDiffHunks());
            Collections.sort(diffHunks);

            for (TimelineItem.Diff diffHunk : diffHunks) {
                items.add(diffHunk);
                for (TimelineItem.TimelineComment comment : diffHunk.comments) {
                    items.add(comment);
                }

                if (!diffHunk.isReply()) {
                    items.add(new TimelineItem.Reply(diffHunk.getInitialTimelineComment()));
                }
            }

            return items;
        });
    }

    @Override
    protected RootAdapter<TimelineItem, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
        mAdapter = new TimelineItemAdapter(getActivity(), mRepoOwner, mRepoName, mIssueNumber,
                true, false, this);
        return mAdapter;
    }

    @Override
    protected void onAddData(RootAdapter<TimelineItem, ?> adapter, List<TimelineItem> data) {
        super.onAddData(adapter, data);

        if (mInitialComment != null) {
            for (int i = 0; i < data.size(); i++) {
                TimelineItem item = data.get(i);

                if (item instanceof TimelineItem.TimelineComment) {
                    TimelineItem.TimelineComment comment = (TimelineItem.TimelineComment) item;
                    if (mInitialComment.matches(comment.comment().id(), comment.getCreatedAt())) {
                        scrollToAndHighlightPosition(i);
                        break;
                    }
                }
            }
            mInitialComment = null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_EDIT) {
            if (resultCode == Activity.RESULT_OK) {
                reloadComments(true);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void reloadComments( boolean alsoClearCaches) {
        if (mAdapter != null && !alsoClearCaches) {
            // Don't clear adapter's cache, we're only interested in the new event
            mAdapter.suppressCacheClearOnNextClear();
        }

        onRefresh();
    }

    @Override
    protected int getEmptyTextResId() {
        return 0;
    }

    @Override
    public void editComment(GitHubCommentBase comment) {
        Intent intent;
        if (comment instanceof ReviewComment) {
            intent = EditPullRequestCommentActivity.makeIntent(getActivity(), mRepoOwner, mRepoName,
                    mIssueNumber, comment.id(), 0L, comment.body(), 0);
        } else {
            intent = EditIssueCommentActivity.makeIntent(getActivity(), mRepoOwner, mRepoName,
                    mIssueNumber, comment.id(), comment.body(), 0);
        }

        startActivityForResult(intent, REQUEST_EDIT);
    }

    @Override
    public void deleteComment(final GitHubCommentBase comment) {
        new AlertDialog.Builder(getActivity())
                .setMessage(R.string.delete_comment_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> handleDeleteComment(comment))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void quoteText(CharSequence text) {

    }

    @Override
    public void addText(CharSequence text) {
    }

    @Override
    public void replyToComment(long replyToId) {
        Intent intent = EditPullRequestCommentActivity.makeIntent(getActivity(),
                mRepoOwner, mRepoName, mIssueNumber, 0L, replyToId, null, 0);
        startActivity(intent);
    }

    @Override
    public String getShareSubject(GitHubCommentBase comment) {
        return null;
    }

    @Override
    public Single<List<Reaction>> loadReactionDetailsInBackground(final GitHubCommentBase comment) {
        final ReactionService service = Gh4Application.get().getGitHubService(ReactionService.class);
        return ApiHelpers.PageIterator
                .toSingle(page -> {
                    return comment instanceof ReviewComment
                            ? service.getPullRequestReviewCommentReactions(
                                    mRepoOwner, mRepoName, comment.id(), page)
                            : service.getIssueCommentReactions(
                                    mRepoOwner, mRepoName, comment.id(), page);
                });
    }

    @Override
    public Single<Reaction> addReactionInBackground(GitHubCommentBase comment, String content) {
        final ReactionService service = Gh4Application.get().getGitHubService(ReactionService.class);
        final ReactionRequest request = ReactionRequest.builder().content(content).build();
        final Single<Response<Reaction>> responseSingle = comment instanceof ReviewComment
                ? service.createPullRequestReviewCommentReaction(mRepoOwner, mRepoName, comment.id(), request)
                : service.createIssueCommentReaction(mRepoOwner, mRepoName, comment.id(), request);
        return responseSingle.map(ApiHelpers::throwOnFailure);
    }

    private void handleDeleteComment(GitHubCommentBase comment) {
        final Single<Response<Boolean>> responseSingle;
        if (comment instanceof ReviewComment) {
            PullRequestReviewCommentService service =
                    Gh4Application.get().getGitHubService(PullRequestReviewCommentService.class);
            responseSingle = service.deleteComment(mRepoOwner, mRepoName, comment.id());
        } else {
            IssueCommentService service =
                    Gh4Application.get().getGitHubService(IssueCommentService.class);
            responseSingle = service.deleteIssueComment(mRepoOwner, mRepoName, comment.id());
        }

        responseSingle
                .map(ApiHelpers::throwOnFailure)
                .compose(RxUtils.wrapForBackgroundTask(getBaseActivity(),
                        R.string.deleting_msg, R.string.error_delete_comment))
                .subscribe(result -> reloadComments(false), error -> {});
    }
}
