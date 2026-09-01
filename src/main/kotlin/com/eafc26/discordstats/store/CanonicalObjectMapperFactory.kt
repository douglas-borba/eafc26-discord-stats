package com.eafc26.discordstats.store

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardMetrics
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.interpretation.MatchFeatures
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.match.DefendingStats
import com.eafc26.discordstats.domain.match.PassingStats
import com.eafc26.discordstats.domain.match.PlayerIdentity
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.story.StoryContent
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

object CanonicalObjectMapperFactory {

    fun create(source: ObjectMapper): ObjectMapper = source.copy()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .addMixIn(PlayerRole::class.java, PlayerRoleMixin::class.java)
        .addMixIn(AwardMetrics::class.java, AwardMetricsMixin::class.java)
        .addMixIn(DecisionEvidence::class.java, DecisionEvidenceMixin::class.java)
        .addMixIn(StoryContent::class.java, StoryContentMixin::class.java)
        .addMixIn(CanonicalMatch::class.java, CanonicalMatchDerivedMixin::class.java)
        .addMixIn(MatchInterpretation::class.java, MatchInterpretationDerivedMixin::class.java)
        .addMixIn(EligibilityInterpretation::class.java, EligibilityDerivedMixin::class.java)
        .addMixIn(AwardDecision::class.java, AwardDecisionDerivedMixin::class.java)
        .addMixIn(MatchFeatures::class.java, MatchFeaturesDerivedMixin::class.java)
        .addMixIn(PlayerIdentity::class.java, PlayerIdentityDerivedMixin::class.java)
        .addMixIn(PassingStats::class.java, PassingStatsDerivedMixin::class.java)
        .addMixIn(DefendingStats::class.java, DefendingStatsDerivedMixin::class.java)
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(PlayerRole.Goalkeeper::class, name = "goalkeeper"),
    JsonSubTypes.Type(PlayerRole.Outfield::class, name = "outfield"),
    JsonSubTypes.Type(PlayerRole.Unknown::class, name = "unknown"),
)
internal abstract class PlayerRoleMixin

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(AwardMetrics.Craque::class, name = "craque"),
    JsonSubTypes.Type(AwardMetrics.Xerife::class, name = "xerife"),
    JsonSubTypes.Type(AwardMetrics.Bagre::class, name = "bagre"),
)
internal abstract class AwardMetricsMixin

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(DecisionEvidence.PlayerPopulation::class, name = "player_population"),
    JsonSubTypes.Type(DecisionEvidence.Scoreboard::class, name = "scoreboard"),
    JsonSubTypes.Type(DecisionEvidence.ReportedResult::class, name = "reported_result"),
    JsonSubTypes.Type(DecisionEvidence.PlayingTime::class, name = "playing_time"),
    JsonSubTypes.Type(DecisionEvidence.AwardCandidate::class, name = "award_candidate"),
    JsonSubTypes.Type(DecisionEvidence.Rating::class, name = "rating"),
    JsonSubTypes.Type(DecisionEvidence.AttackingContribution::class, name = "attacking_contribution"),
    JsonSubTypes.Type(DecisionEvidence.PassingPerformance::class, name = "passing_performance"),
    JsonSubTypes.Type(DecisionEvidence.DefensivePerformance::class, name = "defensive_performance"),
    JsonSubTypes.Type(DecisionEvidence.AdvancedPerformance::class, name = "advanced_performance"),
    JsonSubTypes.Type(DecisionEvidence.Discipline::class, name = "discipline"),
    JsonSubTypes.Type(DecisionEvidence.EaRecognition::class, name = "ea_recognition"),
    JsonSubTypes.Type(DecisionEvidence.TeamPassingPerformance::class, name = "team_passing"),
    JsonSubTypes.Type(DecisionEvidence.GoalkeepingPerformance::class, name = "goalkeeping"),
)
internal abstract class DecisionEvidenceMixin

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(StoryContent.MatchResult::class, name = "match_result"),
    JsonSubTypes.Type(StoryContent.Award::class, name = "award"),
    JsonSubTypes.Type(StoryContent.Contributions::class, name = "contributions"),
    JsonSubTypes.Type(StoryContent.Highlights::class, name = "highlights"),
    JsonSubTypes.Type(StoryContent.EaRecognizedMvp::class, name = "ea_recognized_mvp"),
    JsonSubTypes.Type(StoryContent.BagrePerformance::class, name = "bagre_performance"),
    JsonSubTypes.Type(StoryContent.OffensiveNarrative::class, name = "offensive_narrative"),
    JsonSubTypes.Type(StoryContent.BehindThePlay::class, name = "behind_the_play"),
    JsonSubTypes.Type(StoryContent.OneOnOne::class, name = "one_on_one"),
    JsonSubTypes.Type(StoryContent.RedCard::class, name = "red_card"),
    JsonSubTypes.Type(StoryContent.PassPrecision::class, name = "pass_precision"),
    JsonSubTypes.Type(StoryContent.LostMail::class, name = "lost_mail"),
    JsonSubTypes.Type(StoryContent.Goalkeeper::class, name = "goalkeeper"),
)
internal abstract class StoryContentMixin

@JsonIgnoreProperties("matchId")
internal abstract class CanonicalMatchDerivedMixin

@JsonIgnoreProperties("matchId", "appliedRules", "evidence")
internal abstract class MatchInterpretationDerivedMixin

@JsonIgnoreProperties("eligiblePlayerIds")
internal abstract class EligibilityDerivedMixin

@JsonIgnoreProperties("awarded")
internal abstract class AwardDecisionDerivedMixin

@JsonIgnoreProperties("rules", "evidence")
internal abstract class MatchFeaturesDerivedMixin

@JsonIgnoreProperties("preferredDisplayName")
internal abstract class PlayerIdentityDerivedMixin

@JsonIgnoreProperties("missed", "accuracy")
internal abstract class PassingStatsDerivedMixin

@JsonIgnoreProperties("tackleAccuracy")
internal abstract class DefendingStatsDerivedMixin
