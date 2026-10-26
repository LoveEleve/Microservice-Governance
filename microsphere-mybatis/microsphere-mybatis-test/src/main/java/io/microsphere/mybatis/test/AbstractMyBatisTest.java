/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microsphere.mybatis.test;

import io.microsphere.lang.function.ThrowableAction;
import io.microsphere.lang.function.ThrowableConsumer;
import io.microsphere.logging.Logger;
import io.microsphere.mybatis.test.entity.Child;
import io.microsphere.mybatis.test.entity.Father;
import io.microsphere.mybatis.test.entity.User;
import io.microsphere.mybatis.test.mapper.ChildMapper;
import io.microsphere.mybatis.test.mapper.FatherMapper;
import io.microsphere.mybatis.test.mapper.UserMapper;
import io.microsphere.mybatis.util.MyBatisUtils;
import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.sql.DataSource;
import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

import static io.microsphere.logging.LoggerFactory.getLogger;
import static io.microsphere.mybatis.test.MyBatisTestUtils.CHILD_TYPE_ALIAS;
import static io.microsphere.mybatis.test.MyBatisTestUtils.DESTROY_DB_SCRIPT_RESOURCE_NAME;
import static io.microsphere.mybatis.test.MyBatisTestUtils.FATHER_TYPE_ALIAS;
import static io.microsphere.mybatis.test.MyBatisTestUtils.INIT_DB_SCRIPT_RESOURCE_NAME;
import static io.microsphere.mybatis.test.MyBatisTestUtils.USER_TYPE_ALIAS;
import static io.microsphere.mybatis.test.MyBatisTestUtils.buildDefaultSqlSessionFactory;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract MyBatis Test
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy<a/>
 * @see AbstractSqlSessionTest
 * @see AbstractExecutorTest
 * @see AbstractMapperTest
 * @since 1.0.0
 */
public abstract class AbstractMyBatisTest {

    private static final SecureRandom random = new SecureRandom();

    protected final Logger logger = getLogger(this.getClass());

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    public void init() throws Throwable {
        this.sqlSessionFactory = createBuildSqlSessionFactory();
        initData();
    }

    private SqlSessionFactory createBuildSqlSessionFactory() throws IOException {
        SqlSessionFactory factory = buildDefaultSqlSessionFactory();
        customize(factory);
        customize(factory.getConfiguration());
        return factory;
    }

    /**
     * Customize the {@link SqlSessionFactory}
     *
     * @param sqlSessionFactory {@link SqlSessionFactory}
     */
    protected void customize(SqlSessionFactory sqlSessionFactory) {
    }

    /**
     * Customize the {@link Configuration}
     *
     * @param configuration {@link Configuration}
     */
    protected void customize(Configuration configuration) {
    }

    private void initData() throws IOException, SQLException {
        runScript(INIT_DB_SCRIPT_RESOURCE_NAME);
    }

    protected void doInExecutor(ThrowableConsumer<Executor> consumer) throws Throwable {
        MyBatisUtils.doInExecutor(getConfiguration(), consumer);
    }

    protected <M> void doInMapper(Class<M> mapperClass, ThrowableConsumer<M> mapperConsumer) throws Throwable {
        doInSqlSession(sqlSession -> {
            M mapper = sqlSession.getMapper(mapperClass);
            mapperConsumer.accept(mapper);
        });
    }

    protected void doInSqlSession(ThrowableConsumer<SqlSession> consumer) throws Throwable {
        MyBatisUtils.doInSqlSession(getConfiguration(), consumer);
    }

    public static User createUser() {
        int id = random.nextInt(99999);
        String name = "User - " + id;
        return new User(id, name);
    }

    protected void runSafely(ThrowableAction action) {
        try {
            action.execute();
        } catch (Throwable e) {
            logger.warn("error message : {}", e.getMessage());
        }
    }

    protected void runScript(String resource) throws IOException, SQLException {
        DataSource dataSource = this.getDataSource();
        MyBatisUtils.runScript(dataSource, resource);
    }

    protected Connection getConnection(Executor executor) throws SQLException {
        return this.getTransaction(executor).getConnection();
    }

    protected Transaction getTransaction(Executor executor) {
        return executor.getTransaction();
    }

    protected DataSource getDataSource() {
        return this.getEnvironment().getDataSource();
    }

    protected Environment getEnvironment() {
        return this.getConfiguration().getEnvironment();
    }

    protected MappedStatement getMappedStatement(String id) {
        return this.getConfiguration().getMappedStatement(id);
    }

    protected Configuration getConfiguration() {
        return this.sqlSessionFactory.getConfiguration();
    }

    @AfterEach
    public void destroy() throws IOException, SQLException {
        destroyDB();
    }

    private void destroyDB() throws IOException, SQLException {
        runScript(DESTROY_DB_SCRIPT_RESOURCE_NAME);
    }

    public static void assertConfiguration(Configuration configuration) {
        assertFalse(configuration.isLazyLoadingEnabled());

        TypeAliasRegistry typeAliasRegistry = configuration.getTypeAliasRegistry();
        Map<String, Class<?>> typeAliases = typeAliasRegistry.getTypeAliases();
        assertSame(User.class, typeAliases.get(USER_TYPE_ALIAS));
        assertSame(Child.class, typeAliases.get(CHILD_TYPE_ALIAS));
        assertSame(Father.class, typeAliases.get(FATHER_TYPE_ALIAS));

        MapperRegistry mapperRegistry = configuration.getMapperRegistry();
        Collection<Class<?>> mappers = mapperRegistry.getMappers();
        assertTrue(mappers.contains(UserMapper.class));
        assertTrue(mappers.contains(ChildMapper.class));
        assertTrue(mappers.contains(FatherMapper.class));
    }
}
