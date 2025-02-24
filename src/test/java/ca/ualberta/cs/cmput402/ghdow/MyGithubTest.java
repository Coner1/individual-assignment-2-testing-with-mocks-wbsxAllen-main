package ca.ualberta.cs.cmput402.ghdow;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.*;
import org.mockito.MockedConstruction;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class) class MyGithubTest {
    @Test
    void getIssueCreateDates() throws IOException {
        // We don't have a login token for github :(
        String token = "I am a fake token";
        MyGithub my = new MyGithub(token);
        assertNotNull(my);

        // We made this field protected instead of private so we can inject our mock
        // directly
        my.gitHub = mock(GitHub.class);

        // Set up a fake repository
        String fakeRepoName = "fakeRepo";
        GHRepository fakeRepo = mock(GHRepository.class);

        // Put our fake repository in a list of fake repositories
        // We made this field protected instead of private so we can inject our mock
        // directly, but we could have mocked GHMyself/GHPerson instead
        my.myRepos = new HashMap<>();
        my.myRepos.put(fakeRepoName, fakeRepo);

        // Generate some mock issues with mock dates for our mock repository
        final int DATES = 30;

        ArrayList<GHIssue> mockIssues = new ArrayList<>();
        ArrayList<Date> expectedDates = new ArrayList<>();
        HashMap<String, Date> issueToDate = new HashMap<>();
        Calendar calendar = Calendar.getInstance();
        for (int i = 1; i < DATES+1; i++) {
            calendar.set(100, Calendar.JANUARY, i, 1, 1, 1);
            Date issueDate = calendar.getTime();

            // Give this mock GHIssue a unique Mockito "name"
            // This has nothing to do with github, you can
            // give any mockito object a name
            String issueMockName = String.format("getIssueCreateDates issue #%d", i);
            GHIssue issue = mock(GHIssue.class, issueMockName);

            expectedDates.add(issueDate);
            mockIssues.add(issue);

            // Note that we DO NOT try to
            // when(issue.getCreatedAt())
            // because that's what causes the Mockito/github-api bug ...
            // instead we'll just save what we would have wanted to do
            // in a hashmap and then apply it later to GHIssueWrapper
            // which does not have the bug because it doesn't use
            // github-api 's WithBridgeMethods

            issueToDate.put(issueMockName, issueDate);
        }

        // Supply the mock repo with a list of mock issues to return
        when(fakeRepo.getIssues(GHIssueState.CLOSED)).thenReturn(mockIssues);

        List<Date> actualDates;

        // Inside the try block, Mockito will intercept GHIssueWrapper's constructor
        // and have it construct mock GHIssueWrappers instead
        // We have to use a try-with-resources, or it will get stuck like this and probably
        // ruin our other tests.
        try (MockedConstruction<GHIssueWrapper> ignored = mockConstruction(
                GHIssueWrapper.class,
                (mock, context) -> {
                    // Figure out which GHIssue the mock GHIssueWrapper 's
                    // constructor was called with
                    GHIssue issue = (GHIssue) context.arguments().get(0);
                    assertNotNull(issue);

                    // Ask mockito what name we gave the mock issue
                    String issueName = mockingDetails(issue)
                            .getMockCreationSettings()
                            .getMockName()
                            .toString();

                    // Make sure GHIssueWrapper was constructed with one of our mock
                    // GHIssue objects
                    assertTrue(issueToDate.containsKey(issueName));

                    // Get the date associated with the mock GHIssue object
                    // This is where we work around the Mockito/github-api bug!
                    Date date = issueToDate.get(issueName);
                    assertNotNull(date);
                    // Apply the date to the mock GHIssueWrapper
                    when(mock.getCreatedAt()).thenReturn(date);
                }
        )) {
            // This is the only line actually inside the try block
            actualDates = my.getIssueCreateDates();
        }

        // Check that we got our fake dates out
        assertEquals(expectedDates.size(), DATES);
        assertEquals(actualDates.size(), DATES);

        for (int i = 1; i < DATES; i++) {
            assertEquals(expectedDates.get(i), actualDates.get(i));
            System.out.println(expectedDates.get(i));
        }
    }

    /**
     * 2:
     * @throws IOException
     */
    @Test
    void testMostPopularMonth() throws IOException {
        MyGithub my = new MyGithub("fakeToken");
        my.gitHub = mock(GitHub.class);
        List<GHCommit> mockCommits = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.JANUARY, 1); mockCommits.add(mockCommitWithDate(cal.getTime()));
        cal.set(2025, Calendar.JANUARY, 2); mockCommits.add(mockCommitWithDate(cal.getTime()));
        cal.set(2025, Calendar.FEBRUARY, 1); mockCommits.add(mockCommitWithDate(cal.getTime()));
        when(my.getCommits()).thenReturn(mockCommits);
        assertEquals("January", my.getMostPopularMonth()); // 2 Jan vs 1 Feb
    }
    private GHCommit mockCommitWithDate(Date date) throws IOException {
        GHCommit commit = mock(GHCommit.class);
        when(commit.getCommitDate()).thenReturn(date);
        return commit;
    }


    // #3: Average Time Between Commits
    @Test
    void testAverageCommitInterval() throws IOException {
        MyGithub my = new MyGithub("fakeToken");
        my.gitHub = mock(GitHub.class);
        my.myRepos = new HashMap<>();

        GHRepository repo = mock(GHRepository.class);
        my.myRepos.put("testRepo", repo);

        List<GHCommit> commits = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0); commits.add(mockCommitWithDate(cal.getTime()));
        cal.set(2025, Calendar.JANUARY, 2, 0, 0, 0); commits.add(mockCommitWithDate(cal.getTime()));
        cal.set(2025, Calendar.JANUARY, 4, 0, 0, 0); commits.add(mockCommitWithDate(cal.getTime()));

        PagedIterable<GHCommit> pagedCommits = mock(PagedIterable.class);
        when(pagedCommits.toList()).thenReturn(commits);
        GHRepository.CommitQueryBuilder queryBuilder = mock(GHRepository.CommitQueryBuilder.class);
        when(queryBuilder.author(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.list()).thenReturn(pagedCommits);
        when(repo.queryCommits()).thenReturn(queryBuilder);

        double avgHours = my.getAverageCommitInterval("testRepo");
        assertEquals(48.0, avgHours, 0.01); // (24 + 72) / 2 = 48 hours

        // Edge case: Single commit
        when(pagedCommits.toList()).thenReturn(Collections.singletonList(mockCommitWithDate(new Date())));
        assertEquals(0.0, my.getAverageCommitInterval("testRepo"), 0.01);
    }

    // #4: Average Number of Open Issues
    @Test
    void testAverageOpenIssues() throws IOException {
        MyGithub my = new MyGithub("fakeToken");
        my.gitHub = mock(GitHub.class);
        my.myRepos = new HashMap<>();

        GHRepository repo1 = mock(GHRepository.class);
        GHRepository repo2 = mock(GHRepository.class);
        my.myRepos.put("repo1", repo1);
        my.myRepos.put("repo2", repo2);

        List<GHIssue> issues1 = Arrays.asList(mock(GHIssue.class), mock(GHIssue.class));
        List<GHIssue> issues2 = Collections.singletonList(mock(GHIssue.class));
        when(repo1.getIssues(GHIssueState.OPEN)).thenReturn(issues1);
        when(repo2.getIssues(GHIssueState.OPEN)).thenReturn(issues2);

        double avgIssues = my.getAverageOpenIssues();
        assertEquals(1.5, avgIssues, 0.01); // (2 + 1) / 2 = 1.5

        // Edge case: No repos
        my.myRepos.clear();
        assertEquals(0.0, my.getAverageOpenIssues(), 0.01);
    }

    // #5: Average Pull Request Duration
    @Test
    void testAveragePullRequestDuration() throws IOException {
        MyGithub my = new MyGithub("fakeToken");
        my.gitHub = mock(GitHub.class);
        my.myRepos = new HashMap<>();

        GHRepository repo = mock(GHRepository.class);
        my.myRepos.put("testRepo", repo);

        List<GHPullRequest> prs = new ArrayList<>();
        GHPullRequest pr1 = mock(GHPullRequest.class);
        GHPullRequest pr2 = mock(GHPullRequest.class);
        prs.add(pr1);
        prs.add(pr2);
        when(repo.getPullRequests(GHIssueState.CLOSED)).thenReturn(prs);

        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0); Date open1 = cal.getTime();
        cal.set(2025, Calendar.JANUARY, 2, 0, 0, 0); Date close1 = cal.getTime();
        cal.set(2025, Calendar.JANUARY, 3, 0, 0, 0); Date open2 = cal.getTime();
        cal.set(2025, Calendar.JANUARY, 6, 0, 0, 0); Date close2 = cal.getTime();

        try (MockedConstruction<GHPullRequestWrapper> ignored = mockConstruction(
                GHPullRequestWrapper.class,
                (mock, context) -> {
                    GHPullRequest pr = (GHPullRequest) context.arguments().get(0);
                    if (pr == pr1) {
                        when(mock.getCreatedAt()).thenReturn(open1);
                        when(mock.getClosedAt()).thenReturn(close1);
                    } else if (pr == pr2) {
                        when(mock.getCreatedAt()).thenReturn(open2);
                        when(mock.getClosedAt()).thenReturn(close2);
                    }
                }
        )) {
            double avgHours = my.getAveragePullRequestDuration();
            assertEquals(60.0, avgHours, 0.01); // (24 + 96) / 2 = 60 hours
        }

        // Edge case: No PRs
        when(repo.getPullRequests(GHIssueState.CLOSED)).thenReturn(Collections.emptyList());
        try (MockedConstruction<GHPullRequestWrapper> ignored = mockConstruction(GHPullRequestWrapper.class)) {
            assertEquals(0.0, my.getAveragePullRequestDuration(), 0.01);
        }
    }

    // #6: Average Number of Collaborators
    @Test
    void testAverageCollaborators() throws IOException {
        MyGithub my = new MyGithub("fakeToken");
        my.gitHub = mock(GitHub.class);
        my.myRepos = new HashMap<>();

        GHRepository repo1 = mock(GHRepository.class);
        GHRepository repo2 = mock(GHRepository.class);
        my.myRepos.put("repo1", repo1);
        my.myRepos.put("repo2", repo2);

        List<GHUser> collab1 = Arrays.asList(mock(GHUser.class), mock(GHUser.class));
        List<GHUser> collab2 = Collections.singletonList(mock(GHUser.class));
        PagedIterable<GHUser> pagedCollab1 = mock(PagedIterable.class);
        PagedIterable<GHUser> pagedCollab2 = mock(PagedIterable.class);
        when(pagedCollab1.toList()).thenReturn(collab1);
        when(pagedCollab2.toList()).thenReturn(collab2);
        when(repo1.listCollaborators()).thenReturn(pagedCollab1);
        when(repo2.listCollaborators()).thenReturn(pagedCollab2);

        double avgCollabs = my.getAverageCollaborators();
        assertEquals(1.5, avgCollabs, 0.01); // (2 + 1) / 2 = 1.5

        // Edge case: No repos
        my.myRepos.clear();
        assertEquals(0.0, my.getAverageCollaborators(), 0.01);
    }
}

