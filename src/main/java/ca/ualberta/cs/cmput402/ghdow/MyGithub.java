package ca.ualberta.cs.cmput402.ghdow;

import java.io.IOException;

import org.kohsuke.github.*;

import java.text.DateFormatSymbols;
import java.util.*;

public class MyGithub {
    protected GitHub gitHub;
    protected GHPerson myself;
    protected Map<String, GHRepository> myRepos;
    private List<GHCommit> myCommits;
    public MyGithub(String token) throws IOException {
        gitHub = new GitHubBuilder().withOAuthToken(token).build();
    }

    private GHPerson getMyself() throws IOException {
        if (myself == null) {
            myself = gitHub.getMyself();
        }
        return myself;
    }

    public String getGithubName() throws IOException {
        return gitHub.getMyself().getLogin();
    }

    private List<GHRepository> getRepos() throws IOException {
        if (myRepos == null) {
            myRepos = getMyself().getRepositories();
        }
        return new ArrayList<>(myRepos.values());
    }

    public int argMax(int[] days) {
        int max = Integer.MIN_VALUE;
        int arg = -1;
        for (int i = 0; i < days.length; i++) {
            if (days[i] > max) {
                max = days[i];
                arg = i;
            }
        }
        return arg;
    }

    public String intToDay(int day) {
        return switch (day) {
            case Calendar.SUNDAY -> "Sunday";
            case Calendar.MONDAY -> "Monday";
            case Calendar.TUESDAY -> "Tuesday";
            case Calendar.WEDNESDAY -> "Wednesday";
            case Calendar.THURSDAY -> "Thursday";
            case Calendar.FRIDAY -> "Friday";
            case Calendar.SATURDAY -> "Saturday";
            default -> throw new IllegalArgumentException("Not a day: " + day);
        };
    }

    public String getMostPopularDay() throws IOException {
        final int SIZE = 8;
        int[] days = new int[SIZE];
        Calendar cal = Calendar.getInstance();
        for (GHCommit commit: getCommits()) {
            Date date = commit.getCommitDate();
            cal.setTime(date);
            int day = cal.get(Calendar.DAY_OF_WEEK);
            days[day] += 1;
        }
        return intToDay(argMax(days));
    }

    protected Iterable<? extends GHCommit> getCommits() throws IOException {
        if (myCommits == null || myCommits.isEmpty()) {
            myCommits = new ArrayList<>();
            int count = 0;
            for (GHRepository repo: getRepos()) {
                System.out.println("Loading commits: repo " + repo.getName());
                try {
                    for (GHCommit commit : repo.queryCommits().author(getGithubName()).list().toList()) {
                        myCommits.add(commit);
                        count++;
                        if (count % 100 == 0) {
                            System.out.println("Loading commits: " + count);
                        }
                    }
                } catch (GHException e) {
                        throw e;
                }
            }
        }
        return myCommits;
    }

    public ArrayList<Date> getIssueCreateDates() throws IOException {
        ArrayList<Date> result = new ArrayList<>();
        for (GHRepository repo: getRepos()) {
            List<GHIssue> issues = repo.getIssues(GHIssueState.CLOSED);
            for (GHIssue issue: issues)
                result.add((new GHIssueWrapper(issue)).getCreatedAt());
            }
        return result;
    }

    /**
     * 2: Most popular commit month.
     * @return
     * @throws IOException
     */
    public String getMostPopularMonth() throws IOException {
        int[] months = new int[13]; // 1-12 for months, 0 unused
        Calendar cal = Calendar.getInstance();
        for (GHCommit commit : getCommits()) {
            cal.setTime(commit.getCommitDate());
            int month = cal.get(Calendar.MONTH) + 1; // 0-based to 1-based
            months[month]++;
        }
        int maxMonth = argMax(months);
        return new DateFormatSymbols().getMonths()[maxMonth - 1];
    }

    /**
     * 3: Average Time Between Commits on a Repository
     * @param repoName
     * @return
     * @throws IOException
     */
    public double getAverageCommitInterval(String repoName) throws IOException {
        GHRepository repo = myRepos.get(repoName);
        if (repo == null) {
            throw new IllegalArgumentException("Repository not found: " + repoName);
        }
        String githubName = getGithubName();
        List<? extends GHCommit> commits = repo.queryCommits().author(githubName).list().toList();
        if (commits.size() < 2) {
            return 0.0; // Need at least 2 commits for an interval
        }
        commits.sort(Comparator.comparing((commit) -> {
            try {
                return commit.getCommitDate();
            } catch (IOException e) {
                return null;
            }
        }));


        long totalMillis = 0;
        for (int i = 1; i < commits.size(); i++) {
            long diff = commits.get(i).getCommitDate().getTime() - commits.get(i - 1).getCommitDate().getTime();
            totalMillis += diff;
        }
        double avgMillis = totalMillis / (double)(commits.size() - 1);
        return avgMillis / (1000.0 * 60 * 60); // Convert to hours
    }

    /**
     * 4: Average Number of Open Issues Across Repos
     * @return
     * @throws IOException
     */
    public double getAverageOpenIssues() throws IOException {
        List<GHRepository> repos = getRepos();
        if (repos.isEmpty()) {
            return 0.0;
        }
        int totalOpenIssues = 0;
        for (GHRepository repo : repos) {
            totalOpenIssues += repo.getIssues(GHIssueState.OPEN).size();
        }
        return totalOpenIssues / (double)repos.size();
    }

    /**
     * #5: Average Time Pull Requests Stay Open
     * @return
     * @throws IOException
     */
    public double getAveragePullRequestDuration() throws IOException {
        List<GHRepository> repos = getRepos();
        List<GHPullRequestWrapper> allPRs = new ArrayList<>();
        for (GHRepository repo : repos) {
            for (GHPullRequest pr : repo.getPullRequests(GHIssueState.CLOSED)) {
                allPRs.add(new GHPullRequestWrapper(pr));
            }
        }
        if (allPRs.isEmpty()) {
            return 0.0;
        }
        long totalHours = 0;
        for (GHPullRequestWrapper pr : allPRs) {
            long durationMillis = pr.getClosedAt().getTime() - pr.getCreatedAt().getTime();
            totalHours += durationMillis / (1000 * 60 * 60);
        }
        return totalHours / (double)allPRs.size();
    }

    /**
     * 6: Average Number of Collaborators Across Repos
     * @return
     * @throws IOException
     */
    public double getAverageCollaborators() throws IOException {
        List<GHRepository> repos = getRepos();
        if (repos.isEmpty()) {
            return 0.0;
        }
        int totalCollaborators = 0;
        for (GHRepository repo : repos) {
            totalCollaborators += repo.listCollaborators().toList().size();
        }
        return totalCollaborators / (double)repos.size();
    }

    public String getMostPopularDayWithRobustness() throws IOException {
        int[] days = new int[8];
        Calendar cal = Calendar.getInstance();
        int attempts = 0;
        while (attempts < 3) {
            try {
                for (GHCommit commit : getCommits()) {
                    Date date = commit.getCommitDate();
                    cal.setTime(date);
                    days[cal.get(Calendar.DAY_OF_WEEK)]++;
                }
                return intToDay(argMax(days));
            } catch (IOException e) {
                attempts++;
                if (attempts == 3) {
                    throw new IOException("Failed after 3 attempts: " + e.getMessage());
                }
            }
        }
        return null;
    }
}