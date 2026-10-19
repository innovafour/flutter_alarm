import 'package:flutter/services.dart';
import 'package:flutter_callkit_incoming/entities/entities.dart';
import 'package:flutter_callkit_incoming/flutter_callkit_incoming.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('AndroidParams serializes customActions as id/label maps', () {
    const params = AndroidParams(
      isShowLogo: false,
      customActions: [
        AndroidCustomAction(id: 'up', label: 'Que suba'),
        AndroidCustomAction(id: 'down', label: 'Bajo yo'),
      ],
    );
    final json = params.toJson();
    expect(json['customActions'], [
      {'id': 'up', 'label': 'Que suba'},
      {'id': 'down', 'label': 'Bajo yo'},
    ]);
    final back = AndroidParams.fromJson(json);
    expect(back.customActions!.map((a) => a.id), ['up', 'down']);
    expect(back.customActions!.last.label, 'Bajo yo');
  });

  test('customActions is null by default', () {
    expect(const AndroidParams().toJson()['customActions'], isNull);
    expect(AndroidParams.fromJson({}).customActions, isNull);
  });

  test('CallKitParams nests customActions under android', () {
    const params = CallKitParams(
      id: '1',
      android: AndroidParams(
        customActions: [AndroidCustomAction(id: 'a', label: 'A')],
      ),
    );
    expect(params.toJson()['android']['customActions'], [
      {'id': 'a', 'label': 'A'},
    ]);
  });

  test('onEvent decodes ACTION_CALL_CUSTOM and skips unknown events',
      () async {
    const channel = EventChannel('flutter_callkit_incoming_events');
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockStreamHandler(
      channel,
      MockStreamHandler.inline(onListen: (args, sink) {
        sink.success({
          'event': 'com.hiennv.flutter_callkit_incoming.UNKNOWN',
          'body': {},
        });
        sink.success({
          'event': 'com.hiennv.flutter_callkit_incoming.ACTION_CALL_CUSTOM',
          'body': {'id': 'call-1', 'customActionId': 'up'},
        });
        sink.endOfStream();
      }),
    );
    final events = await FlutterCallkitIncoming.onEvent.toList();
    expect(events.first, isNull);
    expect(events.last!.event, Event.actionCallCustom);
    expect(events.last!.body['customActionId'], 'up');
    expect(events.last!.body['id'], 'call-1');
    messenger.setMockStreamHandler(channel, null);
  });
}
