package com.datasophon.worker.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import cn.hutool.db.handler.RsHandler;
import cn.hutool.db.sql.SqlExecutor;

public class SqlUtils {
    
    public static List<String> getTableList(Connection con) throws SQLException {
        return SqlExecutor.query(con, "SHOW TABLES", (RsHandler<List<String>>) rs -> {
            final List<String> result = new ArrayList<>();
            while (rs.next()) {
                result.add(rs.getString(1));
            }
            return result;
        });
    }
    
    public static String removeCommentSql(String sql) {
        SqlCommentParser sqlCommentParser = new SqlCommentParser(sql);
        return sqlCommentParser.removeCommentSql();
    }
    
    public static class SqlCommentParser {
        
        private final String sql;
        private int pos;
        private int start = 0;
        
        public SqlCommentParser(String sql) {
            this.sql = sql;
            this.pos = 0;
        }
        
        public String removeCommentSql() {
            StringBuilder newSQL = new StringBuilder();
            int startIndex = 0;
            while (true) {
                Comment comment = this.nextComment();
                // the sql is parse over
                if (comment == null) {
                    // process the sql without comment case
                    if (startIndex != sql.length()) {
                        newSQL.append(sql, startIndex, sql.length());
                    }
                    break;
                } else {
                    newSQL.append(sql, startIndex, comment.startIndex);
                    startIndex = comment.endIndex;
                }
            }
            return newSQL.toString();
            
        }
        
        /**
         * Get next comment of sql
         * <p>
         * it only support two comment modes, one is -- ,the other is /*
         *
         * @return null, only if sql parser over;
         */
        public Comment nextComment() {
            while (pos < sql.length()) {
                char c = sql.charAt(pos);
                Integer startIndex = null;
                switch (c) {
                    // ignore the type of quote
                    case '\'':
                        start = pos;
                        ++pos;
                        while (pos < sql.length()) {
                            c = sql.charAt(pos);
                            ++pos;
                            if (c == '\'') {
                                break;
                            }
                        }
                        break;
                    
                    case '`':
                        start = pos;
                        ++pos;
                        while (pos < sql.length()) {
                            c = sql.charAt(pos);
                            ++pos;
                            if (c == '`') {
                                break;
                            }
                        }
                        break;
                    
                    case '\"':
                        start = pos;
                        ++pos;
                        while (pos < sql.length()) {
                            c = sql.charAt(pos);
                            ++pos;
                            if (c == '\"') {
                                break;
                            }
                        }
                        break;
                    
                    // parse the type of comment
                    case '/':
                        // possible start of '/*'
                        if (pos + 1 < sql.length()) {
                            char c1 = sql.charAt(pos + 1);
                            if (c1 == '*') {
                                startIndex = pos;
                                int end = sql.indexOf("*/", pos + 2);
                                if (end < 0) {
                                    end = sql.length();
                                } else {
                                    end += "*/".length();
                                }
                                pos = end;
                                Integer endIndex = pos;
                                return new Comment(startIndex, endIndex);
                            }
                        }
                        
                    case '-':
                        // possible start of '--' comment
                        if (c == '-' && pos + 1 < sql.length() && sql.charAt(pos + 1) == '-') {
                            startIndex = pos;
                            pos = indexOfLineEnd(sql, pos + 2);
                            Integer endIndex = pos;
                            return new Comment(startIndex, endIndex);
                        }
                        
                    default:
                        if (isOpenQuote(c)) {
                            break;
                        } else {
                            // parse the type of ID
                            ++pos;
                            loop: while (pos < sql.length()) {
                                c = sql.charAt(pos);
                                switch (c) {
                                    case '\'':
                                    case '`':
                                    case '\"':
                                    case '/':
                                        break loop;
                                    case '-':
                                        // possible start of '--' comment
                                        if (c == '-' && pos + 1 < sql.length() && sql.charAt(pos + 1) == '-') {
                                            break loop;
                                        }
                                    default:
                                        ++pos;
                                }
                            }
                        }
                }
            }
            return null;
        }
        
        private boolean isOpenQuote(char character) {
            if (character == '\"') {
                return true;
            } else if (character == '`') {
                return true;
            } else if (character == '\'') {
                return true;
            }
            return false;
        }
        
        private int indexOfLineEnd(String sql, int i) {
            int length = sql.length();
            while (i < length) {
                char c = sql.charAt(i);
                switch (c) {
                    case '\r':
                    case '\n':
                        return i;
                    default:
                        ++i;
                }
            }
            return i;
        }
    }
    
    public static class Comment {
        
        private Integer startIndex;
        private Integer endIndex;
        
        Comment(Integer startIndex, Integer endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }
}
