package com.oldbook.service.notification;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ThongBaoTicketServiceTest {

    private final MutableClock clock = new MutableClock();
    private final ThongBaoTicketService service = new ThongBaoTicketService(clock);

    @Test
    void ticketOpensExactlyOneConnectionForTheAccountItWasIssuedTo() {
        String ticket = service.capVe(42);

        assertThat(service.dungVe(ticket)).contains(42);
        assertThat(service.dungVe(ticket)).isEmpty();
    }

    @Test
    void ticketsAreUnguessableAndDoNotMixAccounts() {
        String first = service.capVe(1);
        String second = service.capVe(2);

        assertThat(first).isNotEqualTo(second).hasSizeGreaterThanOrEqualTo(40);
        assertThat(service.dungVe(second)).contains(2);
        assertThat(service.dungVe(first)).contains(1);
    }

    @Test
    void ticketExpiresAndCannotBeUsedAfterwards() {
        String ticket = service.capVe(7);
        clock.advance(ThongBaoTicketService.THOI_HAN);

        assertThat(service.dungVe(ticket)).isEmpty();

        // Hết hạn rồi thì quay ngược đồng hồ cũng không dùng lại được: vé đã bị hủy khi thử dùng.
        clock.advance(ThongBaoTicketService.THOI_HAN.negated());
        assertThat(service.dungVe(ticket)).isEmpty();
    }

    @Test
    void unknownOrMissingTicketIsRejected() {
        assertThat(service.dungVe(null)).isEmpty();
        assertThat(service.dungVe(" ")).isEmpty();
        assertThat(service.dungVe("khong-co-ve-nay")).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-12T00:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
