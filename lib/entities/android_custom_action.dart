import 'package:json_annotation/json_annotation.dart';

part 'android_custom_action.g.dart';

/// A custom answer button shown on the Android incoming-call screen and as an
/// action of the incoming-call notification (max 3 are shown).
///
/// Tapping it fires [Event.actionCallCustom] with the call data plus
/// `customActionId` set to [id]. Ignored on iOS.
@JsonSerializable()
class AndroidCustomAction {
  const AndroidCustomAction({required this.id, required this.label});

  /// Identifier sent back as `customActionId` when the action is tapped.
  final String id;

  /// Text shown on the button / notification action.
  final String label;

  factory AndroidCustomAction.fromJson(Map<String, dynamic> json) =>
      _$AndroidCustomActionFromJson(json);

  Map<String, dynamic> toJson() => _$AndroidCustomActionToJson(this);
}
