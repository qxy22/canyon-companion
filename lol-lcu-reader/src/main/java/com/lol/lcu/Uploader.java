package com.lol.lcu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Scanner;

/**
 * 战绩上传入口：连接本地 LCU -> 读最近 N 场 -> 登录后端 -> 上传到 /api/match/import。
 *
 * 用法：
 *   mvn exec:java -Dexec.mainClass=com.lol.lcu.Uploader \
 *     -Dexec.args="--username 你的账号 --password 你的密码"
 * 可选参数：--server（默认 http://localhost:8080）、--count（默认 10）。
 */
public final class Uploader {

    private static final String DEFAULT_SERVER = "http://localhost:8080";

    public static void main(String[] args) {
        String server = arg(args, "--server", DEFAULT_SERVER);
        String username = arg(args, "--username", null);
        String password = arg(args, "--password", null);
        int count = Integer.parseInt(arg(args, "--count", "10"));

        if (username == null || password == null) {
            // 参数没给全时，从标准输入交互式读取（IDEA 控制台和终端都能用）
            Scanner scanner = new Scanner(System.in);
            if (username == null || username.isBlank()) {
                System.out.print("网站用户名: ");
                System.out.flush();
                username = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            }
            if (password == null || password.isBlank()) {
                System.out.print("网站密码: ");
                System.out.flush();
                password = scanner.hasNextLine() ? scanner.nextLine() : "";
            }
            if (username.isBlank() || password.isBlank()) {
                System.err.println("请通过 --username 和 --password 传入网站登录账号密码");
                return;
            }
        }

        try {
            ObjectMapper mapper = new ObjectMapper();

            // 1) 读本地客户端战绩
            LcuCredentials creds = CredentialFinder.discover();
            LcuClient client = new LcuClient(creds);
            MatchHistoryReader reader = new MatchHistoryReader(client);

            MatchHistoryReader.Summoner me = reader.currentSummoner();
            System.out.println("召唤师: " + me.name() + "#" + me.tagLine());

            List<Long> ids = reader.matchIds(me.puuid(), count);
            System.out.println("读取到 " + ids.size() + " 场对局");

            ObjectNode payload = mapper.createObjectNode();
            payload.put("summonerName", me.name());
            ArrayNode matches = payload.putArray("matches");
            int parsed = 0;
            for (long id : ids) {
                MatchExtractor.SelfMatch m = MatchExtractor.extract(reader.gameDetail(id), me.puuid(), me.name());
                if (m == null) {
                    continue;
                }
                ObjectNode o = matches.addObject();
                o.put("gameId", m.gameId);
                o.put("win", m.win);
                o.put("lane", m.lane);
                o.put("kills", m.kills);
                o.put("deaths", m.deaths);
                o.put("assists", m.assists);
                o.put("cs", m.cs);
                o.put("visionScore", m.visionScore);
                o.put("duration", m.duration);
                o.put("playedAt", m.playedAt);
                parsed++;
            }
            System.out.println("解析到 " + parsed + " 场");

            if (parsed == 0) {
                System.out.println("没有可上传的对局数据。");
                return;
            }

            // 2) 登录后端拿 token
            String token = login(server, username, password, mapper);
            System.out.println("登录成功，开始上传…");

            // 3) 上传
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(server + "/api/match/import"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpClient http = HttpClient.newHttpClient();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            System.out.println("上传响应 HTTP " + resp.statusCode());
            System.out.println(resp.body());
        } catch (Exception e) {
            System.err.println("出错了: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String login(String server, String username, String password, ObjectMapper mapper)
            throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("username", username);
        body.put("password", password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(server + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode node = mapper.readTree(resp.body());
        if (node.path("code").asInt() != 200) {
            throw new RuntimeException("登录失败: " + node.path("message").asText());
        }
        return node.path("data").path("token").asText();
    }

    private static String arg(String[] args, String key, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) {
                return args[i + 1];
            }
        }
        return def;
    }
}
