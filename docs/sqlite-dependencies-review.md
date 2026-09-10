# Revisão nominal das dependências SQLite/JPA — task 2.1

Status: aprovado nominalmente pelo usuário nesta conversa, em resposta à apresentação das duas coordenadas e versões: `Aprovada.`.

Esta revisão registra as dependências já presentes no projeto. Não altera pom.xml, versões, configuração ou dados e não atribui aprovação retroativa à configuração existente.

| Coordenada Maven efetiva | Escopo declarado | Finalidade |
|---|---|---|
| org.xerial:sqlite-jdbc:3.49.1.0 | runtime | Driver JDBC org.sqlite.JDBC para acesso ao SQLite local. |
| org.hibernate.orm:hibernate-community-dialects:6.6.53.Final | compile (padrão) | Disponibiliza org.hibernate.community.dialect.SQLiteDialect para Hibernate/JPA. |

As versões são gerenciadas pelo parent org.springframework.boot:spring-boot-starter-parent:3.5.16, com Java 21. O dialeto acompanha a versão 6.6.53.Final de hibernate-core. Não há substituição da stack nem inclusão de nova dependência nesta revisão.

Evidências locais: declarações em pom.xml; propriedades sqlite-jdbc.version e hibernate.version no BOM Spring Boot 3.5.16 instalado no repositório Maven local; classpath dos relatórios Surefire confirma ambas as versões. application.properties configura org.sqlite.JDBC e org.hibernate.community.dialect.SQLiteDialect. A última suíte registrada contém 228 testes aprovados, incluindo persistência e fluxos com SQLite temporário. Não foram executados novos testes nesta revisão documental; essa evidência não representa certificação de compatibilidade em outros ambientes.

Limitações existentes: SQLite não suporta os ALTER TABLE de constraints únicas emitidos pelo Hibernate; o projeto usa índice criado por schema.sql e desativa a atualização automática dessas constraints. A separação do banco em data/arqfor.db e teste de reinício continuam na task 2.3. Banco e filesystem não formam uma transação atômica. A persistência acadêmica de chave/IV/senha no SQLite permanece conforme ADR-0003.

Decisão recebida: o usuário respondeu `Aprovada.` à solicitação explícita de aprovação das duas coordenadas e versões acima, na fundação Java 21/Spring Boot 3.5.16. A evidência regulariza o registro nominal a partir desta resposta; não afirma que a aprovação precedeu a configuração já existente.

Mensagem registrada em tasks.md e task 2.1 concluída. Não autoriza alterações de dependências, commit, push, merge, Docker ou operações sobre evidências. A task 2.2 não é encerrada por esta revisão.
